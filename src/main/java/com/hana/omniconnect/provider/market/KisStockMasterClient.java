package com.hana.omniconnect.provider.market;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.hana.omniconnect.market.domain.StockSummary;

@Component
public class KisStockMasterClient {

    private static final Charset CP949 = Charset.forName("MS949");
    private static final int MAX_MASTER_BYTES = 8 * 1024 * 1024;

    private final HttpClient httpClient;
    private final KisStockMasterFileParser parser;

    @Autowired
    public KisStockMasterClient(KisStockMasterFileParser parser) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                parser);
    }

    KisStockMasterClient(HttpClient httpClient, KisStockMasterFileParser parser) {
        this.httpClient = httpClient;
        this.parser = parser;
    }

    public List<StockSummary> fetch(KisStockMasterMarket market, String url)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("KIS stock master download failed: status=" + response.statusCode());
        }
        String content = unzipMaster(response.body(), market.entryName());
        return parser.parse(market, content);
    }

    private String unzipMaster(byte[] payload, String entryName) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(payload), CP949)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(entryName)) {
                    byte[] bytes = zipInputStream.readNBytes(MAX_MASTER_BYTES + 1);
                    if (bytes.length > MAX_MASTER_BYTES) {
                        throw new IOException("KIS stock master file is too large: " + entryName);
                    }
                    return new String(bytes, CP949);
                }
            }
        }
        throw new IOException("KIS stock master entry not found: " + entryName);
    }
}
