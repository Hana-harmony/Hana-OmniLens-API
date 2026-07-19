package com.hana.omniconnect.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.springframework.util.StreamUtils;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.body = StreamUtils.copyToByteArray(request.getInputStream());
    }

    public byte[] body() {
        return body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(body);
    }

    @Override
    public BufferedReader getReader() {
        Charset charset = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        CachedBodyServletInputStream(byte[] body) {
            this.inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Async body reads are not supported");
        }
    }
}
