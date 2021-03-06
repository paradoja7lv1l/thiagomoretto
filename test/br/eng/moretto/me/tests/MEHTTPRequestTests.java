package br.eng.moretto.me.tests;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import br.eng.moretto.me.MEHTTPRequest;
import br.eng.moretto.me.MEHTTPRequest.PartiallyDownloadedException;

public class MEHTTPRequestTests {
    String urlString = "http://localhost:50036";

    Exception lastException = null;
    boolean stdOutRequestDidRedirectedListenerWasCalled,
            stdOutRequestDidFinishedListenerWasCalled,
            stdErrRequestDidFailedListenerWasCalled;

    MEHTTPRequest.Listener stdOutRequestDidRedirectedListener = new MEHTTPRequest.Listener() {
        @Override
        public void on(MEHTTPRequest request) {
            stdOutRequestDidRedirectedListenerWasCalled = true;
            printHeaders(request.getHeaders());
        }
    };
    MEHTTPRequest.Listener stdOutRequestDidFinishedListener = new MEHTTPRequest.Listener() {
        @Override
        public void on(MEHTTPRequest request) {
            stdOutRequestDidFinishedListenerWasCalled = true;
        }
    };
    MEHTTPRequest.Listener stdErrRequestDidFailedListener =  new MEHTTPRequest.Listener() {
        @Override
        public void on(MEHTTPRequest request) {
            stdErrRequestDidFailedListenerWasCalled = true;
            lastException = request.getException();
            request.getException().printStackTrace(System.err);
        }
    };
    
    static HttpTestServer httpTestServer;

    @BeforeClass
    static public void setupClass() throws Exception {
        httpTestServer = new HttpTestServer();
        httpTestServer.start();
    }

    @Before
    public void setup() throws Exception {
        lastException = null;
        stdOutRequestDidRedirectedListenerWasCalled = false;
        stdOutRequestDidFinishedListenerWasCalled = false;
        stdErrRequestDidFailedListenerWasCalled = false;
    }

    @Test
    public void shouldMakeGetRequestReceiveOk() throws MalformedURLException {
        httpTestServer.setMockResponseCode(200);
        httpTestServer.setMockResponseData("MY OMG DATA");
        URL testURL = new URL(urlString);
        MEHTTPRequest request = new MEHTTPRequest(testURL);
        request .setShouldFollowRedirects(true)
                .setDidRequestRedirectedListener(stdOutRequestDidRedirectedListener)
                .setDidRequestFinishedListener(stdOutRequestDidFinishedListener)
                .setDidRequestFailedListener(stdErrRequestDidFailedListener);
        request.addHeader("X-MyProduct-HEADER", "FOOBAR");
        request.startSynchronous();
        printHeaders(request.getHeaders());

        Assert.assertEquals(200, request.getResponseCode());
        Assert.assertEquals("MY OMG DATA", request.getResponseData());
        Assert.assertTrue(stdOutRequestDidFinishedListenerWasCalled);
    }

    @Test
    public void shouldMakeGetRequestReceiveNotModified() throws MalformedURLException {
        httpTestServer.setMockResponseCode(304);
        URL testURL = new URL(urlString);
        MEHTTPRequest request = new MEHTTPRequest(testURL);
        request .setShouldFollowRedirects(true)
                .setDidRequestRedirectedListener(stdOutRequestDidRedirectedListener)
                .setDidRequestFinishedListener(stdOutRequestDidFinishedListener)
                .setDidRequestFailedListener(stdErrRequestDidFailedListener);
        request.addHeader("X-MyProduct-HEADER", "FOOBAR");
        request.startSynchronous();
        printHeaders(request.getHeaders());

        Assert.assertEquals(304, request.getResponseCode());
        Assert.assertTrue(stdOutRequestDidRedirectedListenerWasCalled);
    }

    @Test
    public void shouldMakeGetRequestAndSaveContentOfFile() throws MalformedURLException {
        httpTestServer.setMockResponseCode(200);

        URL testURL = new URL(urlString);
        MEHTTPRequest request = new MEHTTPRequest(testURL);
        request .setShouldFollowRedirects(true)
                .setDidRequestRedirectedListener(stdOutRequestDidRedirectedListener)
                .setDidRequestFinishedListener(stdOutRequestDidFinishedListener)
                .setDidRequestFailedListener(stdErrRequestDidFailedListener)
                .setDownloadDestinationPath("/tmp/temp_file")
                .startSynchronous();

        Assert.assertEquals(200, request.getResponseCode());
        Assert.assertTrue(new File("/tmp/temp_file").exists());
        Assert.assertTrue(stdOutRequestDidFinishedListenerWasCalled);
    }
    
    @Test
    public void shouldMakeGetRequestAndSaveContentOfFileAllowingResume() throws MalformedURLException 
    {
        httpTestServer.setMockResponseCode(200);
        httpTestServer.setMockContentType("image/jpeg");
        httpTestServer.setMockResponseFile("fixtures/internet.jpg");
        httpTestServer.setMockStopDownloadAtByte(1024);

        File fi = new File("fixtures/internet.jpg");
        File fd = new File("/tmp/temp_file_resume.jpg" + MEHTTPRequest.DOWNLOAD_PATH_APPENDIX);
        fd.delete();

        URL testURL = new URL(urlString);
        MEHTTPRequest request = new MEHTTPRequest(testURL);
        request .setShouldFollowRedirects(true)
                .setDidRequestRedirectedListener(stdOutRequestDidRedirectedListener)
                .setDidRequestFinishedListener(stdOutRequestDidFinishedListener)
                .setDidRequestFailedListener(stdErrRequestDidFailedListener)
                .setDownloadDestinationPath("/tmp/temp_file_resume.jpg")
                .startSynchronous();

        fd = new File("/tmp/temp_file_resume.jpg" + MEHTTPRequest.DOWNLOAD_PATH_APPENDIX);

        Assert.assertEquals(200, request.getResponseCode());
        Assert.assertTrue(fd.exists());
        Assert.assertEquals(1024, fd.length());
        Assert.assertTrue(stdErrRequestDidFailedListenerWasCalled);
        Assert.assertEquals(((PartiallyDownloadedException) lastException).getBytesRemaining(), fi.length() - 1024);

        httpTestServer.setMockStopDownloadAtByte(-1);

        // letus create a new http request and resume download.
        request = new MEHTTPRequest(testURL);
        request .setShouldFollowRedirects(true)
                .setDidRequestRedirectedListener(stdOutRequestDidRedirectedListener)
                .setDidRequestFinishedListener(stdOutRequestDidFinishedListener)
                .setDidRequestFailedListener(stdErrRequestDidFailedListener)
                .setDownloadDestinationPath("/tmp/temp_file_resume.jpg")
                .setShouldAllowResumeDownloads(true)
                .startSynchronous();
        
        fd = new File("/tmp/temp_file_resume.jpg");
        
        Assert.assertEquals(200, request.getResponseCode());
        Assert.assertEquals(fi.length(), fd.length());
        Assert.assertEquals(fi.length() - 1024, request.getTotalContentReaded());
    }
    
    @Test
    public void shouldMakeGetRequestAndWriteContentToFileOutputStream() throws MalformedURLException, FileNotFoundException {
        httpTestServer.setMockResponseCode(200);

        FileOutputStream fous = new FileOutputStream(new File("/tmp/temp_file_os"));

        URL testURL = new URL(urlString);
        MEHTTPRequest request = new MEHTTPRequest(testURL);
        request .setShouldFollowRedirects(true)
                .setDidRequestRedirectedListener(stdOutRequestDidRedirectedListener)
                .setDidRequestFinishedListener(stdOutRequestDidFinishedListener)
                .setDidRequestFailedListener(stdErrRequestDidFailedListener)
                .setDestinationOutputStream(fous)
                .startSynchronous();

        Assert.assertEquals(200, request.getResponseCode());
        Assert.assertTrue(new File("/tmp/temp_file_os").exists());
        Assert.assertTrue(stdOutRequestDidFinishedListenerWasCalled);
    }
    
    @Test
    public void shouldMakePostRequest() throws MalformedURLException, UnsupportedEncodingException {
        httpTestServer.setMockResponseCode(201);
        httpTestServer.setMockResponseData("MY OMG FORM DATA");
        httpTestServer.setMockContentType(null);
        httpTestServer.setMockResponseFile(null);
        
        //URL testURL = new URL("http://localhost:4567/pah");
        URL testURL = new URL(urlString);
        MEHTTPRequest request = new MEHTTPRequest(testURL);
        request .setShouldFollowRedirects(true)
                .setMethod("POST")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setUrlParameters("name=" + URLEncoder.encode("Jos?? Fino da Silva 140%", "UTF-8"))
                .setDidRequestRedirectedListener(stdOutRequestDidRedirectedListener)
                .setDidRequestFinishedListener(stdOutRequestDidFinishedListener)
                .setDidRequestFailedListener(stdErrRequestDidFailedListener)
                .startSynchronous();
        
        Assert.assertEquals(201, request.getResponseCode());
        Assert.assertTrue(stdOutRequestDidFinishedListenerWasCalled);
    }

    private void printHeaders(Map<String, List<String>> headers)
    {
        for (String header : headers.keySet())
        {
            List<String> values = headers.get(header);
            System.out.print("Header: " + header);
            for (String hv : values)
            {
                System.out.println("\t" + hv);
            }
        }
    }
}
