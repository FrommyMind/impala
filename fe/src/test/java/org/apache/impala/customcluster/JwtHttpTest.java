// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.impala.customcluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hive.service.rpc.thrift.*;
import org.apache.impala.testutil.WebClient;
import org.apache.impala.testutil.X509CertChain;
import org.apache.thrift.transport.THttpClient;
import org.hamcrest.Matcher;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.junit.After;
import org.junit.Test;

import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.StringContains.containsString;

/**
 * Tests that hiveserver2 operations over the http interface work as expected when
 * JWT authentication is being used.
 */
public class JwtHttpTest {
  private static final String CA_CERT = "cacert.pem";
  private static final String SERVER_CERT = "server-cert.pem";
  private static final String SERVER_KEY = "server-key.pem";
  private static final String JWKS_FILE_NAME = "jwks_rs256.json";

  WebClient client_ = new WebClient();

  /* Since we don't have Java version of JWT library, we use pre-calculated JWT token.
   * The token and JWK set used in this test case were generated by using BE unit-test
   * function JwtUtilTest::VerifyJwtRS256.
   */
  String jwtToken_ =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6InB1YmxpYzpjNDI0YjY3Yi1mZTI4LTQ1ZDctYjAxNS1m"
      + "NzlkYTUwYjViMjEiLCJ0eXAiOiJKV1MifQ.eyJpc3MiOiJhdXRoMCIsInVzZXJuYW1lIjoia"
      + "W1wYWxhIn0.OW5H2SClLlsotsCarTHYEbqlbRh43LFwOyo9WubpNTwE7hTuJDsnFoVrvHiWI"
      + "02W69TZNat7DYcC86A_ogLMfNXagHjlMFJaRnvG5Ekag8NRuZNJmHVqfX-qr6x7_8mpOdU55"
      + "4kc200pqbpYLhhuK4Qf7oT7y9mOrtNrUKGDCZ0Q2y_mizlbY6SMg4RWqSz0RQwJbRgXIWSgc"
      + "bZd0GbD_MQQ8x7WRE4nluU-5Fl4N2Wo8T9fNTuxALPiuVeIczO25b5n4fryfKasSgaZfmk0C"
      + "oOJzqbtmQxqiK9QNSJAiH2kaqMwLNgAdgn8fbd-lB1RAEGeyPH8Px8ipqcKsPk0bg";

  String jwtTokenX5c_ = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImtpZF94NWMiLCJ0eXAiOiJKV1Q"
      + "ifQ.eyJleHAiOjI1ODk4MzQ2NzUsImlhdCI6MTcxNTYzMjM1MSwiaXNzIjoiYXV0aDAiLCJ"
      + "qdGkiOiJ4OEpoSDZVd21XQUZfSGZ2cnR0aEREbWYzREwzTVRzWSIsInN1YiI6Imp3dC1jcH"
      + "AuZXhhbXBsZS5sb2NhbGhvc3QifQ.JZxb9GPuhtlbxWT6_XWX0uZ9EN7PP4frfHNxjquDIl"
      + "gv7At8sEFw21mVWKoafDHKRPzt35zhRlRO9saXQOyVFxSzHHv23RSS47bgUqcpkHQQltP6P"
      + "9SRbJDT7GB13Kusx5Pzl-kJosNR-ZpiQY_nkJEUPHj9vIYAc6B5eGudGEoyWAvjtNE2uBCr"
      + "t5UodEO1RqcZOwjZivTjIIjCOgu3ibz2lmJfhEGwSHOm5uld7sdjjnAviVvVSRASoHP4e2Y"
      + "u4aFevvNaW-CNNlNLXq1QlLE9ClB-IgecZUFOexGZaLSNGiKuAvAqzN6ks_gUJKgtqBeQGe"
      + "DVqCNPE8JuNTfIG0W7Ywb3U9zFBeZ3CtI4RwQsKOYncuy54AC841iGAWkAChsWtBkgTjupS"
      + "ExjvUsKTu3MK5ffbh4LARrj3fTOZlmOqRCM884WG2KoN695dqxcmQKf5QiYMTrFXEVUCM-Y"
      + "4smZqHsTQI3PxfcU6neYnwDDMS-FUNePX8yLX_3E2FpBTIuBitwInO1Bk6SsbZvZRmG-2Cw"
      + "EWlclIr9hMxSeProDSamS5mI89VuShDLYUXaDISSKXoKriFdOxICL2uTdbQLsb_6Z5GbYW4"
      + "2CcUSgG9lJmImtMKH9bS2wwSOmUBi03XBLrraODdI69_EFWFgCG9WjcWJo0R74GJbHAi3cyLM";

  /*
   * Create JWKS file in the root directory of WebServer if it's set as true.
   */
  boolean createJWKSForWebServer_ = false;

  private void setUp(String extraArgs) throws Exception {
    int ret = CustomClusterRunner.StartImpalaCluster(extraArgs);
    assertEquals(ret, 0);
  }

  /**
   * Helper method to start a JWT auth enabled Impala cluster.
   *
   * @param impaladArgs startup flags to send to the impala coordinator/executors
   * @param catalogdArgs startup flags to send to the impala catalog
   * @param statestoredArgs startup flags to send to the statestore
   * @param expectedRetCode expected exit code for the start impala cluster command,
   *                        if the cluster is expected to start successfully, set to 0
   */
  private void setUp(String impaladArgs, String catalogdArgs,
      String statestoredArgs, int expectedRetCode)
      throws Exception {
    if (createJWKSForWebServer_) createTempJWKSInWebServerRootDir(JWKS_FILE_NAME);

    int ret = CustomClusterRunner.StartImpalaCluster(
        impaladArgs, catalogdArgs, statestoredArgs);
    assertEquals(expectedRetCode, ret);
  }

  /**
   * Helper method to start a JWT auth enabled Impala cluster that has only a single
   * coordinator daemon process.
   *
   * @param impaladArgs startup flags to send to the impala coordinator/executors
   * @param catalogdArgs startup flags to send to the impala catalog
   * @param statestoredArgs startup flags to send to the statestore
   * @param expectedRetCode expected exit code for the start impala cluster command,
   *                        if the cluster is expected to start successfully, set to 0
   */
  private void setUpWithSingleCoordinator(String impaladArgs, String catalogdArgs,
      String statestoredArgs, int expectedRetCode)
      throws Exception {
    if (createJWKSForWebServer_) createTempJWKSInWebServerRootDir(JWKS_FILE_NAME);

    int ret = CustomClusterRunner.StartImpalaCluster(
      impaladArgs, catalogdArgs, statestoredArgs, new HashMap<String, String>(),
      "--num_coordinators=1");
    assertEquals(expectedRetCode, ret);
  }

  @After
  public void cleanUp() throws Exception {
    // Leave a cluster running with the default configuration, then delete temporary
    // JWKS file.
    CustomClusterRunner.StartImpalaCluster();
    if (createJWKSForWebServer_) deleteTempJWKSFromWebServerRootDir();
    client_.Close();
  }

  private void createTempJWKSInWebServerRootDir(String srcFilename) {
    Path srcJwksPath =
        (Path) Paths.get(System.getenv("IMPALA_HOME"), "testdata", "jwt", srcFilename);
    Path tempJwksPath =
        (Path) Paths.get(System.getenv("IMPALA_HOME"), "www", "temp_jwks.json");
    try {
      Files.copy(srcJwksPath, tempJwksPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      fail("Failed to copy file: " + e.getMessage());
    }
  }

  private void deleteTempJWKSFromWebServerRootDir() {
    Path tempJwksPath =
        (Path) Paths.get(System.getenv("IMPALA_HOME"), "www", "temp_jwks.json");
    try {
      Files.delete(tempJwksPath);
    } catch (IOException e) {
      fail("Failed to delete file: " + e.getMessage());
    }
  }

  static void verifySuccess(TStatus status) throws Exception {
    if (status.getStatusCode() == TStatusCode.SUCCESS_STATUS
        || status.getStatusCode() == TStatusCode.SUCCESS_WITH_INFO_STATUS) {
      return;
    }
    throw new Exception(status.toString());
  }

  /**
   * Executes 'query' and fetches the results. Expects there to be exactly one string
   * returned, which will be equal to 'expectedResult'.
   */
  static TOperationHandle execAndFetch(TCLIService.Iface client,
      TSessionHandle sessionHandle, String query, String expectedResult)
      throws Exception {
    TExecuteStatementReq execReq = new TExecuteStatementReq(sessionHandle, query);
    TExecuteStatementResp execResp = client.ExecuteStatement(execReq);
    verifySuccess(execResp.getStatus());

    TFetchResultsReq fetchReq = new TFetchResultsReq(
        execResp.getOperationHandle(), TFetchOrientation.FETCH_NEXT, 1000);
    TFetchResultsResp fetchResp = client.FetchResults(fetchReq);
    verifySuccess(fetchResp.getStatus());
    List<TColumn> columns = fetchResp.getResults().getColumns();
    assertEquals(1, columns.size());
    assertEquals(expectedResult, columns.get(0).getStringVal().getValues().get(0));

    return execResp.getOperationHandle();
  }

  private void verifyJwtAuthMetrics(long expectedAuthSuccess, long expectedAuthFailure)
      throws Exception {
    long actualAuthSuccess =
        (long) client_.getMetric("impala.thrift-server.hiveserver2-http-frontend."
            + "total-jwt-token-auth-success");
    assertEquals(expectedAuthSuccess, actualAuthSuccess);
    long actualAuthFailure =
        (long) client_.getMetric("impala.thrift-server.hiveserver2-http-frontend."
            + "total-jwt-token-auth-failure");
    assertEquals(expectedAuthFailure, actualAuthFailure);
  }

  /**
   * Tests if sessions are authenticated by verifying the JWT token for connections
   * to the HTTP hiveserver2 endpoint. The JWKS for JWT verification is specified as
   * local json file.
   */
  @Test
  public void testJwtAuth() throws Exception {
    createJWKSForWebServer_ = false;
    String jwksFilename =
        new File(System.getenv("IMPALA_HOME"),
        String.format("testdata/jwt/%s", JWKS_FILE_NAME)).getPath();
    setUp(String.format(
        "--jwt_token_auth=true --jwt_validate_signature=true --jwks_file_path=%s "
            + "--jwt_allow_without_tls=true",
        jwksFilename));
    THttpClient transport = new THttpClient("http://localhost:28000");
    Map<String, String> headers = new HashMap<String, String>();

    // Case 1: Authenticate with valid JWT Token in HTTP header.
    headers.put("Authorization", "Bearer " + jwtToken_);
    headers.put("X-Forwarded-For", "127.0.0.1");
    transport.setCustomHeaders(headers);
    transport.open();
    TCLIService.Iface client = new TCLIService.Client(new TBinaryProtocol(transport));

    // Open a session which will get username 'impala' from JWT token and use it as
    // login user.
    TOpenSessionReq openReq = new TOpenSessionReq();
    TOpenSessionResp openResp = client.OpenSession(openReq);
    // One successful authentication.
    verifyJwtAuthMetrics(1, 0);
    // Running a query should succeed.
    TOperationHandle operationHandle = execAndFetch(
        client, openResp.getSessionHandle(), "select logged_in_user()", "impala");
    // Two more successful authentications - for the Exec() and the Fetch().
    verifyJwtAuthMetrics(3, 0);

    // case 2: Authenticate fails with invalid JWT token which does not have signature.
    String invalidJwtToken =
        "eyJhbGciOiJSUzI1NiIsImtpZCI6InB1YmxpYzpjNDI0YjY3Yi1mZTI4LTQ1ZDctYjAxNS1m"
        + "NzlkYTUwYjViMjEiLCJ0eXAiOiJKV1MifQ.eyJpc3MiOiJhdXRoMCIsInVzZXJuYW1lIjoia"
        + "W1wYWxhIn0.";
    headers.put("Authorization", "Bearer " + invalidJwtToken);
    headers.put("X-Forwarded-For", "127.0.0.1");
    transport.setCustomHeaders(headers);
    try {
      openResp = client.OpenSession(openReq);
      fail("Exception expected.");
    } catch (Exception e) {
      verifyJwtAuthMetrics(3, 1);
      assertEquals(e.getMessage(), "HTTP Response code: 401");
    }

    // case 3: Authenticate fails without "Bearer" token.
    headers.put("Authorization", "Basic VGVzdDFMZGFwOjEyMzQ1");
    headers.put("X-Forwarded-For", "127.0.0.1");
    transport.setCustomHeaders(headers);
    try {
      openResp = client.OpenSession(openReq);
      fail("Exception expected.");
    } catch (Exception e) {
      // JWT authentication is not invoked.
      verifyJwtAuthMetrics(3, 1);
      assertEquals(e.getMessage(), "HTTP Response code: 401");
    }

    // case 4: Authenticate fails without "Authorization" header.
    headers.put("X-Forwarded-For", "127.0.0.1");
    transport.setCustomHeaders(headers);
    try {
      openResp = client.OpenSession(openReq);
      fail("Exception expected.");
    } catch (Exception e) {
      // JWT authentication is not invoked.
      verifyJwtAuthMetrics(3, 1);
      assertEquals(e.getMessage(), "HTTP Response code: 401");
    }
  }

  /**
   * Tests if sessions are authenticated by verifying the JWT token for connections
   * to the HTTP hiveserver2 endpoint. The JWKS for JWT verification is not specified
   * and JWT signatures are not verified.
   */
  @Test
  public void testJwtAuthNotVerifySig() throws Exception {
    createJWKSForWebServer_ = false;
    // Start Impala without jwt_validate_signature as false so that the signature of
    // JWT token will not be validated.
    setUp("--jwt_token_auth=true --jwt_validate_signature=false "
        + "--jwt_allow_without_tls=true");
    THttpClient transport = new THttpClient("http://localhost:28000");
    Map<String, String> headers = new HashMap<String, String>();

    // Case 1: Authenticate with valid JWT Token in HTTP header.
    headers.put("Authorization", "Bearer " + jwtToken_);
    headers.put("X-Forwarded-For", "127.0.0.1");
    transport.setCustomHeaders(headers);
    transport.open();
    TCLIService.Iface client = new TCLIService.Client(new TBinaryProtocol(transport));

    // Open a session which will get username 'impala' from JWT token.
    TOpenSessionReq openReq = new TOpenSessionReq();
    TOpenSessionResp openResp = client.OpenSession(openReq);
    // One successful authentication.
    verifyJwtAuthMetrics(1, 0);
    // Running a query should succeed.
    TOperationHandle operationHandle = execAndFetch(
        client, openResp.getSessionHandle(), "select logged_in_user()", "impala");
    // Two more successful authentications - for the Exec() and the Fetch().
    verifyJwtAuthMetrics(3, 0);
  }

  /**
   * Tests if sessions are authenticated by verifying the JWT token for connections
   * to the HTTP hiveserver2 endpoint. The JWKS for JWT verification is specified as
   * HTTP URL to the statestore Web server.
   */
  @Test
  public void testJwtAuthWithJwksHttpUrl() throws Exception {
    createJWKSForWebServer_ = true;
    String statestoreWebserverArgs = "--webserver_port=25010";
    String jwksHttpUrl = "http://localhost:25010/www/temp_jwks.json";
    String impaladJwtArgs = String.format("--jwt_token_auth=true "
            + "--jwt_validate_signature=true --jwks_url=%s "
            + "--jwks_update_frequency_s=1 --jwt_allow_without_tls=true",
        jwksHttpUrl);
    setUp(impaladJwtArgs, "", statestoreWebserverArgs, 0);

    THttpClient transport = new THttpClient("http://localhost:28000");
    Map<String, String> headers = new HashMap<String, String>();

    // Authenticate with valid JWT Token in HTTP header.
    headers.put("Authorization", "Bearer " + jwtToken_);
    headers.put("X-Forwarded-For", "127.0.0.1");
    transport.setCustomHeaders(headers);
    transport.open();
    TCLIService.Iface client = new TCLIService.Client(new TBinaryProtocol(transport));

    // Open a session which will get username 'impala' from JWT token and use it as
    // login user.
    TOpenSessionReq openReq = new TOpenSessionReq();
    TOpenSessionResp openResp = client.OpenSession(openReq);
    // One successful authentication.
    verifyJwtAuthMetrics(1, 0);
    // Running a query should succeed.
    TOperationHandle operationHandle = execAndFetch(
        client, openResp.getSessionHandle(), "select logged_in_user()", "impala");
    // Two more successful authentications - for the Exec() and the Fetch().
    verifyJwtAuthMetrics(3, 0);

    // Update JWKS in the root directory of Web server.
    createTempJWKSInWebServerRootDir("jwks_es256.json");
    // Sleep long enough for coordinator to update JWKS from Web server.
    Thread.sleep(3000);
    // Authenticate fails due JWT verification failure since the RS256 public key cannot
    // be found in the JWKS.
    transport.setCustomHeaders(headers);
    try {
      openResp = client.OpenSession(openReq);
      fail("Exception expected.");
    } catch (Exception e) {
      verifyJwtAuthMetrics(3, 1);
      assertEquals(e.getMessage(), "HTTP Response code: 401");
    }
  }

  /**
   * Tests if sessions are authenticated by verifying the JWT token for connections
   * to the HTTP hiveserver2 endpoint. The JWKS for JWT verification is specified as
   * HTTPS URL to the statestore Web server. Impala does not verify the certificate of
   * Web server when downloading JWKS.
   */
  @Test
  public void testJwtAuthWithInsecureJwksHttpsUrl() throws Exception {
    createJWKSForWebServer_ = true;
    String certDir = setupServerAndRootCerts("testJwtAuthWithInsecureJwksHttpsUrl",
        "testJwtAuthWithInsecureJwksHttpsUrl Root", "localhostlocalhost");
    String statestoreWebserverArgs =
        String.format("--webserver_certificate_file=%s "
            + "--webserver_private_key_file=%s "
            + "--webserver_interface=localhost --webserver_port=25010 "
            + "--hostname=localhost ",
            Paths.get(certDir, SERVER_CERT), Paths.get(certDir, SERVER_KEY));
    String jwksHttpUrl = "https://localhost:25010/www/temp_jwks.json";
    String impaladJwtArgs = String.format("--jwt_token_auth=true "
        + "--jwt_validate_signature=true --jwks_url=%s "
        + "--jwt_allow_without_tls=true --jwks_verify_server_certificate=false ",
        jwksHttpUrl);
    setUp(impaladJwtArgs, "", statestoreWebserverArgs, 0);

    THttpClient transport = new THttpClient("http://localhost:28000");
    Map<String, String> headers = new HashMap<String, String>();

    // Authenticate with valid JWT Token in HTTP header.
    headers.put("Authorization", "Bearer " + jwtToken_);
    headers.put("X-Forwarded-For", "127.0.0.1");
    transport.setCustomHeaders(headers);
    transport.open();
    TCLIService.Iface client = new TCLIService.Client(new TBinaryProtocol(transport));

    // Open a session which will get username 'impala' from JWT token and use it as
    // login user.
    TOpenSessionReq openReq = new TOpenSessionReq();
    TOpenSessionResp openResp = client.OpenSession(openReq);
    // One successful authentication.
    verifyJwtAuthMetrics(1, 0);
    // Running a query should succeed.
    TOperationHandle operationHandle = execAndFetch(
        client, openResp.getSessionHandle(), "select logged_in_user()", "impala");
    // Two more successful authentications - for the Exec() and the Fetch().
    verifyJwtAuthMetrics(3, 0);
  }

  /**
   * Tests that the Impala coordinator fails to start because the TLS certificate
   * returned by the JWKS server is not trusted.
   *
   * In this test, the TLS certificate has the correct CN but its issuing CA certificate
   * is not trusted.
   */
  @Test
  public void testJwtAuthWithUntrustedJwksHttpsUrl() throws Exception {
    createJWKSForWebServer_ = true;
    String certDir = setupServerAndRootCerts("testJwtAuthWithUntrustedJwksHttpsUrl",
        "testJwtAuthWithUntrustedJwksHttpsUrl Root", "localhost");
    Path logDir = Files.createTempDirectory("testJwtAuthWithUntrustedJwksHttpsUrl");
    String statestoreWebserverArgs =
        String.format("--webserver_certificate_file=%s "
            + "--webserver_private_key_file=%s "
            + "--webserver_interface=localhost --webserver_port=25010 "
            + "--hostname=localhost ",
            Paths.get(certDir, SERVER_CERT), Paths.get(certDir, SERVER_KEY));
    String jwksHttpUrl = "https://localhost:25010/www/temp_jwks.json";
    String impaladJwtArgs = String.format("--jwt_token_auth=true "
        + "--jwt_validate_signature=true --jwks_url=%s "
        + "--jwt_allow_without_tls=true --log_dir=%s --logbuflevel=-1 ",
        jwksHttpUrl, logDir.toAbsolutePath());
    String expectedErrString = String.format("Impalad services did not start correctly, "
        + "exiting.  Error: Error downloading JWKS from '%s': Network error: curl "
        + "error: SSL peer certificate or SSH remote key was not OK: SSL certificate "
        + "problem: unable to get local issuer certificate", jwksHttpUrl);

    // cluster start will fail because the TLS cert returned by the
    // JWKS server is not trusted
    setUpWithSingleCoordinator(impaladJwtArgs, "", statestoreWebserverArgs, 1);

    checkCoordinatorLogs(expectedErrString, logDir);
  }

  /**
   * Tests that the Impala coordinator fails to start because the TLS certificate
   * returned by the JWKS server is not valid.
   *
   * In this test, the TLS certificate has an incorrect CN which means the certificate is
   * not valid even though its issuing CA certificate is trusted.
   */
  @Test
  public void testJwtAuthWithTrustedJwksHttpsUrlInvalidCN() throws Exception {
    createJWKSForWebServer_ = true;
    String certCN = "notvalid";
    String certDir = setupServerAndRootCerts(
        "testJwtAuthWithTrustedJwksHttpsUrlInvalidCN",
        "testJwtAuthWithTrustedJwksHttpsUrlInvalidCN Root", certCN);
    Path logDir = Files.createTempDirectory(
        "testJwtAuthWithTrustedJwksHttpsUrlInvalidCN");
    String statestoreWebserverArgs =
        String.format("--webserver_certificate_file=%s "
            + "--webserver_private_key_file=%s "
            + "--webserver_interface=localhost --webserver_port=25010 "
            + "--hostname=localhost ",
            Paths.get(certDir, SERVER_CERT), Paths.get(certDir, SERVER_KEY));
    String jwksHttpUrl = "https://localhost:25010/www/temp_jwks.json";
    String impaladJwtArgs = String.format("--jwt_token_auth=true "
        + "--jwt_validate_signature=true --jwks_url=%s "
        + "--jwt_allow_without_tls=true --log_dir=%s --jwks_ca_certificate=%s "
        + "--logbuflevel=-1 ", jwksHttpUrl, logDir.toAbsolutePath(),
        Paths.get(certDir, CA_CERT));
    String expectedErrString = String.format("Impalad services did not start correctly, "
        + "exiting.  Error: Error downloading JWKS from '%s': Network error: curl "
        + "error: SSL peer certificate or SSH remote key was not OK: SSL: "
        + "certificate subject name '%s' does not match target hostname '%s'",
        jwksHttpUrl, certCN, "localhost");

    // cluster start will fail because the TLS cert returned by the
    // JWKS server is not trusted
    setUpWithSingleCoordinator(impaladJwtArgs, "", statestoreWebserverArgs, 1);

    checkCoordinatorLogs(expectedErrString, logDir);
  }

  /**
   * Tests that the Impala coordinator successfully starts since the TLS certificate
   * returned by the JWKS server is trusted.
   */
  @Test
  public void testJwtAuthWithTrustedJwksHttpsUrl() throws Exception {
    createJWKSForWebServer_ = true;
    String certDir = setupServerAndRootCerts("testJwtAuthWithTrustedJwksHttpsUrl",
        "testJwtAuthWithTrustedJwksHttpsUrl Root", "localhost");
    String statestoreWebserverArgs =
        String.format("--webserver_certificate_file=%s "
            + "--webserver_private_key_file=%s "
            + "--webserver_interface=localhost --webserver_port=25010 "
            + "--hostname=localhost ",
            Paths.get(certDir, SERVER_CERT), Paths.get(certDir, SERVER_KEY));
    String jwksHttpUrl = "https://localhost:25010/www/temp_jwks.json";
    String impaladJwtArgs = String.format("--jwt_token_auth=true "
        + "--jwt_validate_signature=true --jwks_url=%s "
        + "--jwt_allow_without_tls=true --jwks_ca_certificate=%s ",
        jwksHttpUrl, Paths.get(certDir, CA_CERT));

    // cluster start will succeed because the TLS cert returned by the
    // JWKS server is trusted.
    setUp(impaladJwtArgs, "", statestoreWebserverArgs, 0);
  }

  /**
   * Generates new CA root certificate and server certificate/private key.  All three are
   * written to a new, unique temporary folder.
   *
   * @param testName used as a prefix for the temp folder name
   * @param rootCaCertCN CN of the generated self-signed root cert
   * @param rootLeafCertCN CN of the leaf cert that is signed by the root cert
   *
   * @return path to the temporary folder
   */
  private String setupServerAndRootCerts(String testName, String rootCaCertCN,
      String rootLeafCertCN) throws Exception {
    Path certDir = Files.createTempDirectory(testName);
    Path rootCACert = certDir.resolve(Paths.get(CA_CERT));
    Path serverCert = certDir.resolve(Paths.get(SERVER_CERT));
    Path serverKey = certDir.resolve(Paths.get(SERVER_KEY));
    FileWriter rootCACertWriter = new FileWriter(rootCACert.toFile());
    FileWriter serverCertWriter = new FileWriter(serverCert.toFile());
    FileWriter serverKeyWriter = new FileWriter(serverKey.toFile());

    X509CertChain certChain = new X509CertChain(rootCaCertCN, rootLeafCertCN);

    certChain.writeLeafCertAsPem(serverCertWriter);
    certChain.writeLeafPrivateKeyAsPem(serverKeyWriter);
    certChain.writeRootCertAsPem(rootCACertWriter);
    rootCACertWriter.close();
    serverCertWriter.close();
    serverKeyWriter.close();

    return certDir.toString();
  }

  /**
   * Tests if sessions are authenticated by verifying the JWT token for connections
   * to the HTTP hiveserver2 endpoint. The JWKS contains x5c certificate which is used
   * for for JWT verification. The JWKS is specified as HTTP URL to the statestore Web
   * server.
   */
  @Test
  public void testJwtAuthWithJwksX5cHttpUrl() throws Exception {
    createJWKSForWebServer_ = false;
    String jwksFilename =
        new File(System.getenv("IMPALA_HOME"), "testdata/jwt/jwks_x5c_rs256.json").getPath();
    String impaladJwtArgs = String.format("--jwt_token_auth=true "
            + "--jwt_validate_signature=true --jwks_file_path=%s "
            + "--jwt_custom_claim_username=sub "
            + "--jwt_allow_without_tls=true", jwksFilename);
    setUp(impaladJwtArgs);

    THttpClient transport = new THttpClient("http://localhost:28000");
    Map<String, String> headers = new HashMap<String, String>();

    // Authenticate with valid JWT Token in HTTP header.
    headers.put("Authorization", "Bearer " + jwtTokenX5c_);
    headers.put("X-Forwarded-For", "127.0.0.1");
    transport.setCustomHeaders(headers);
    transport.open();
    TCLIService.Iface client = new TCLIService.Client(new TBinaryProtocol(transport));

    // Open a session which will get username 'jwt-cpp.example.localhost' from JWT token
    // and use it as login user.
    TOpenSessionReq openReq = new TOpenSessionReq();
    TOpenSessionResp openResp = client.OpenSession(openReq);
    // One successful authentication.
    verifyJwtAuthMetrics(1, 0);
    // Running a query should succeed.
    TOperationHandle operationHandle = execAndFetch(
        client, openResp.getSessionHandle(), "select logged_in_user()",
          "jwt-cpp.example.localhost");
    // Two more successful authentications - for the Exec() and the Fetch().
     verifyJwtAuthMetrics(3, 0);
  }

  /**
   * Asserts that the specified string is present in the impalad.ERROR file within the
   * specified log directory.
   *
   * @param expectedString The impalad.ERROR file is searched for this string.  If it is
   *                       not found, the test fails.
   * @param logDir         Location of the directory where log files are stored.
   */
  private void checkCoordinatorLogs(String expectedString, Path logDir)
      throws IOException, InterruptedException {
    // check in the impalad logs that the server startup failed for the expected reason
    List<String> logLines = null;
    Matcher<Iterable<? super String>> m = hasItem(containsString(expectedString));

    // writing logs to disk may take some time, try a few times to search for the
    // expected error in the log
    for (int i=0; i<10; i++) {
      logLines = Files.readAllLines(logDir.resolve("impalad.ERROR"));
      if (m.matches(logLines)) {
        break;
      }
      Thread.sleep(250);
    }

    // runs the matcher one more time to ensure a descriptive failure message is
    // generated if the assert fails
    assertThat(String.format("Impalad startup failed but not for the expected reason. "
        + "See logs in the '%s' folder for details.", logDir), logLines,
        hasItem(containsString(expectedString)));
  }
}
