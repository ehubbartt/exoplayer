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

    @Override
    public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request) throws MediaDrmCallbackException {
        return new byte[0];
    }

    @Override
    public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws MediaDrmCallbackException {
        String url = request.getLicenseServerUrl();
        if (forceDefaultLicenseUrl || TextUtils.isEmpty(url)) {
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
        return executePost(dataSourceFactory, url, request.getData(), requestProperties);
    }

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
//                    @Nullable String redirectUrl = getRedirectUrl(e, manualRedirectCount);
//                    if (redirectUrl == null) {
//                        throw e;
//                    }
//                    dataSpec = dataSpec.buildUpon().setUri(redirectUrl).build();
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