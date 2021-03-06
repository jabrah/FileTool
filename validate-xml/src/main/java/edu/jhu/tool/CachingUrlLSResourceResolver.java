package edu.jhu.tool;

import com.sun.org.apache.xerces.internal.dom.DOMInputImpl;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.concurrent.ConcurrentHashMap;

public class CachingUrlLSResourceResolver implements LSResourceResolver {
    private static final String ENCODING = "UTF-8";
    private static final int CACHE_MAX_SIZE = 1000;

    private ConcurrentHashMap<String, String> resourceCache;

    public CachingUrlLSResourceResolver() {
        this.resourceCache = new ConcurrentHashMap<>();
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
        // Return immediately if systemId is already present in cache
        if (resourceCache.containsKey(systemId)) {
            return new DOMInputImpl(publicId, systemId, baseURI, resourceCache.get(systemId), ENCODING);
        }

        String data = getLocalCopy(systemId);
        if (data != null) {
            addToCache(systemId, data);
        } else {
            try (InputStream in = new URL(systemId).openStream()) {

                data = IOUtils.toString(in, ENCODING);
                addToCache(systemId, data);

            } catch (IOException e) {}
        }

        return new DOMInputImpl(publicId, systemId, baseURI, data, ENCODING);
    }

    private void addToCache(String key, String data) {
        if (resourceCache.size() >= CACHE_MAX_SIZE) {
            resourceCache.clear();
        }

        resourceCache.putIfAbsent(key, data);
    }

    private String getLocalCopy(String systemId) {
        try {
            String[] parts = URLDecoder.decode(systemId, ENCODING).split("/");

            try (InputStream in = getClass().getClassLoader().getResourceAsStream(parts[parts.length - 1])) {
                if (in != null) {
                    return IOUtils.toString(in, ENCODING);
                }
            } catch (IOException e) {}
        } catch (UnsupportedEncodingException e) {}

        return null;
    }
}
