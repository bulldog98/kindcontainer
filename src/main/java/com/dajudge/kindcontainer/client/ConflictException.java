package com.dajudge.kindcontainer.client;

import com.dajudge.kindcontainer.client.http.Response;

import java.io.IOException;

public class ConflictException extends TinyHttpClientRuntimeException {
    public ConflictException(final String method, final String path, final Response result) throws IOException {
        super(method, path, result);
    }

}
