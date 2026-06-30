function fn() {
  // 環境変数 E2E_BASE_URL があれば外部エンドポイント（将来の C 拡張）。
  // 無ければ @SpringBootTest が渡す system property のポートでローカル起動アプリ（A）。
  var System = Java.type('java.lang.System');
  var envBaseUrl = System.getenv('E2E_BASE_URL');
  var port = karate.properties['karate.server.port'];
  var baseUrl = envBaseUrl ? envBaseUrl : 'http://localhost:' + port;
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 5000);
  return { baseUrl: baseUrl };
}
