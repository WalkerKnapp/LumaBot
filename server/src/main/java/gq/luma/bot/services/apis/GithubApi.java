package gq.luma.bot.services.apis;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.google.common.io.Files;
import com.google.gson.Gson;
import gq.luma.bot.Luma;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GithubApi {
    private static final Logger logger = LoggerFactory.getLogger(GithubApi.class);
    private static final String githubAppId = "281896";
    private static final long githubAppInstallationId = 33176154;

    private static GithubApi INSTANCE;

    public static GithubApi get() {
        // TODO: Cache the github api for up to 10 minutes (the max lifetime of a github jwt)
        INSTANCE = new GithubApi();
        return INSTANCE;
    }

    private GitHub gitHub;

    private GithubApi() {
        try {
            GitHub preempriveGithub = new GitHubBuilder().withJwtToken(createJwt()).build();
            GHAppInstallation appInstallation = preempriveGithub.getApp().getInstallationById(githubAppInstallationId);

            GHAppInstallationToken appInstallationToken = appInstallation.createToken().create();
            this.gitHub = new GitHubBuilder().withAppInstallationToken(appInstallationToken.getToken()).build();
            //this.gitHub.checkApiUrlValidity();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private static String createJwt() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.RS256;

        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis - (60 * 1000)); // Github docs specify a 60 second offset to allow for clock drift

        Security.addProvider(new BouncyCastleProvider());

        PrivateKey signingKey;

        try (FileReader keyReader = new FileReader("p2srluma.2023-01-15.private-key.pem")) {
            PemReader pemReader = new PemReader(keyReader);

            PemObject pemObject = pemReader.readPemObject();
            byte[] content = pemObject.getContent();
            PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);

            KeyFactory kf = KeyFactory.getInstance("RSA");
            signingKey = kf.generatePrivate(privKeySpec);
        }

        // Github JWTs have a maximum lifetime of 10 minutes
        long expMillis = nowMillis + 300000;
        Date exp = new Date(expMillis);

        JwtBuilder builder = Jwts.builder()
                .setIssuedAt(now)
                .setIssuer(githubAppId)
                .signWith(signingKey, signatureAlgorithm)
                .setExpiration(exp);

        return builder.compact();
    }

    public CompletableFuture<GHIssue> fetchIssue(String repo, String issueId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return this.gitHub.getRepository("p2sr/" + repo)
                        .getIssue(Integer.parseInt(issueId));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<String> createSarIssue(String title, String body) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GHRepository repository = this.gitHub.getRepository("p2sr/SourceAutoRecord");

                GHIssue issue = repository.createIssue(title)
                        .body(body)
                        .label("luma")
                        .label("bug")
                        .create();

                int issueId = (int) issue.getId();
                String issueUrl = issue.getHtmlUrl().toString();

                /*JsonObject requestContents = Json.object();
                requestContents.add("title", title);
                requestContents.add("body", body);
                requestContents.add("labels", Json.array("luma", "bug"));

                Request request = new Request.Builder()
                        .url("https://api.github.com/repos/p2sr/SourceAutoRecord/issues")
                        .header("Accept", "application/vnd.github+json")
                        .header("Authorization", "Bearer " + createJwt())
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .post(RequestBody.create(requestContents.toString(), MediaType.get("application/json")))
                        .build();

                Response response = Luma.okHttpClient.newCall(request).execute();

                JsonObject responseContents = Json.parse(new InputStreamReader(response.body().byteStream())).asObject();

                logger.info("Github response: {}", responseContents.toString());

                int issueId = responseContents.getInt("id", -1);
                String issueUrl = responseContents.getString("html_url", null);*/

                logger.info("Created SAR issue {} -> {}", issueId, issueUrl);
                return issueUrl;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, Luma.executorService);
    }
}
