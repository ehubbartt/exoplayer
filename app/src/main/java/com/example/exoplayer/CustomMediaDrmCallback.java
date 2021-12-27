package com.example.exoplayer;

import com.google.android.exoplayer2.C;
import android.net.Uri;
import android.text.TextUtils;
import com.google.android.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import com.google.android.exoplayer2.drm.MediaDrmCallbackException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.StatsDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

public final class CustomMediaDrmCallback implements com.google.android.exoplayer2.drm.MediaDrmCallback {

    private final HttpDataSource.Factory dataSourceFactory;
    @Nullable private final String defaultLicenseUrl;
    private final boolean forceDefaultLicenseUrl;
    private final Map<String, String> keyRequestProperties;

    /**
     * Basic constructor used if you only have a key and a data source
     * @param defaultLicenseUrl license url String
     * @param dataSourceFactory data source factory
     */
    public CustomMediaDrmCallback(
            @Nullable String defaultLicenseUrl, HttpDataSource.Factory dataSourceFactory) {
        this(defaultLicenseUrl, /* forceDefaultLicenseUrl= */ false, dataSourceFactory);
    }

    public CustomMediaDrmCallback(
            @Nullable String defaultLicenseUrl,
            boolean forceDefaultLicenseUrl,
            HttpDataSource.Factory dataSourceFactory) {
        Assertions.checkArgument(!(forceDefaultLicenseUrl && TextUtils.isEmpty(defaultLicenseUrl)));
        this.dataSourceFactory = dataSourceFactory;
        this.defaultLicenseUrl = defaultLicenseUrl;
        this.forceDefaultLicenseUrl = forceDefaultLicenseUrl;
        this.keyRequestProperties = new HashMap<>();
    }

    //unused for this project but would be used if a licence was not required. Included since you cannot
    //instantiate an abstract class
    @Override
    public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request) throws MediaDrmCallbackException {
        return new byte[0];
    }

    /**
     * execute a key request given licensing for drm media
     * @param uuid a unique id (one from exoplayers android uuids)
     * @param request contains information regarding the request
     * @return an input stream based on data provided from the helper function
     * @throws MediaDrmCallbackException if the url is empty
     */
    @Override
    public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws MediaDrmCallbackException {
        String url = request.getLicenseServerUrl(); // only used if passed a keyrequest
        if (forceDefaultLicenseUrl || TextUtils.isEmpty(url)) { //otherwise default license is forced when creating a drmcallback object
            url = defaultLicenseUrl;
        }

        if (TextUtils.isEmpty(url)) { //if license is missing throw error
            throw new MediaDrmCallbackException(
                    new DataSpec.Builder().setUri(Uri.EMPTY).build(),
                    Uri.EMPTY,
                    /* responseHeaders= */ ImmutableMap.of(),
                    /* bytesLoaded= */ 0,
                    /* cause= */ new IllegalStateException("No license URL"));
        }
        Map<String, String> requestProperties = new HashMap<>();

        // Add standard request properties for supported schemes.
        String contentType =
                C.WIDEVINE_UUID.equals(uuid)
                        ? "text/xml"
                        : (C.CLEARKEY_UUID.equals(uuid) ? "application/json" : "application/octet-stream");
        requestProperties.put("Content-Type", contentType);
        if (C.WIDEVINE_UUID.equals(uuid)) {
            requestProperties.put(
                    "SOAPAction", "http://schemas.microsoft.com/DRM/2007/03/protocols/AcquireLicense");
        }
        // Add additional request properties.
        synchronized (keyRequestProperties) {
            requestProperties.putAll(keyRequestProperties);
        }
        //returns the helper function that basically just creates an inputStream with the data
        return executePost(dataSourceFactory, url, request.getData(), requestProperties);
    }

    /**
     * helper function for executeKeyRequest()
     * creates a data spec and a data source that is used to return an input stream
     * @param dataSourceFactory
     * @param url license url
     * @param httpBody http body headers
     * @param requestProperties http request properties
     * @return input stream create from the data
     * @throws MediaDrmCallbackException
     */
    private static byte[] executePost(
            HttpDataSource.Factory dataSourceFactory,
            String url,
            @Nullable byte[] httpBody,
            Map<String, String> requestProperties)
            throws MediaDrmCallbackException {
        StatsDataSource dataSource = new StatsDataSource(dataSourceFactory.createDataSource());

        DataSpec dataSpec =
                new DataSpec.Builder()
                        .setUri(url)
                        .setHttpRequestHeaders(requestProperties)
                        .setHttpMethod(DataSpec.HTTP_METHOD_POST)
                        .setHttpBody(httpBody)
                        .setFlags(DataSpec.FLAG_ALLOW_GZIP)
                        .build();
        DataSpec originalDataSpec = dataSpec;

        try {
            while (true) {
                DataSourceInputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
                try {
                    return Util.toByteArray(inputStream);
                } catch (InvalidResponseCodeException e) {
//               util.toByteArray() requires these exceptions be checked but are not required for the project
                } finally {
                    Util.closeQuietly(inputStream);
                }
            }
        } catch (Exception e) {
            throw new MediaDrmCallbackException(
                    originalDataSpec,
                    Assertions.checkNotNull(dataSource.getLastOpenedUri()),
                    dataSource.getResponseHeaders(),
                    dataSource.getBytesRead(),
                    /* cause= */ e);
        }
    }

}