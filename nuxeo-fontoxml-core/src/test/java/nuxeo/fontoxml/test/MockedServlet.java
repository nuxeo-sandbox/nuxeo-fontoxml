package nuxeo.fontoxml.test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.nuxeo.fontoxml.servlet.FontoXMLServlet;

public class MockedServlet {

    protected static abstract class DummyServletOutputStream extends ServletOutputStream {
        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    }
    
    protected HttpServletRequest mockRequest;
    
    protected HttpServletResponse mockResponse;
    
    protected ByteArrayOutputStream responseOutputStream;
    
    protected ServletOutputStream sos;
    
    protected void run(String httpVerb, String pathInfo, Map<String, String> params, String body, boolean withOutStream) throws Exception {

        // Prepare mock request
        mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn(httpVerb);
        when(mockRequest.getPathInfo()).thenReturn(pathInfo);
        if (params != null) {
            params.forEach((k, v) -> {
                when(mockRequest.getParameter(k)).thenReturn(v);
            });
        }
        
        if(body == null) {
            body = "";
        }
        StringReader sr = new StringReader(body);
        BufferedReader buffReader = new BufferedReader(sr);
        when(mockRequest.getReader()).thenReturn(buffReader);
        
        // Prepare mock response
        mockResponse = mock(HttpServletResponse.class);
        if (withOutStream) {
            responseOutputStream = new ByteArrayOutputStream();
            sos = new DummyServletOutputStream() {
                @Override
                public void write(int b) {
                    responseOutputStream.write(b);
                }
            };
            PrintWriter printWriter = new PrintWriter(sos);
            when(mockResponse.getOutputStream()).thenReturn(sos);
            when(mockResponse.getWriter()).thenReturn(printWriter);
        } else {
            if(sos != null) {
                sos.close();
                sos = null;
            }
            
            if(responseOutputStream != null) {
                responseOutputStream.close();
                responseOutputStream = null;
            }
        }

        // Call the service
        switch(httpVerb) {
        case "GET":
            (new FontoXMLServlet()).doGet(mockRequest, mockResponse);
            break;
            
        case "POST":
            (new FontoXMLServlet()).doPost(mockRequest, mockResponse);
            break;
            
        case "PUT":
            (new FontoXMLServlet()).doPut(mockRequest, mockResponse);
            break;
        }
    }
    
    protected void run(String httpVerb, String pathInfo, Map<String, String> params) throws Exception {
        run(httpVerb, pathInfo, params, null, false);
    }
    
    protected void run(String httpVerb, String pathInfo) throws Exception {
        run(httpVerb, pathInfo, null, null, false);
    }
    

}
