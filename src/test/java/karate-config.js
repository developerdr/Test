function fn() {
  // baseUrl comes from the JUnit runner (random port) or -DbaseUrl=... when testing a deployed app
  var baseUrl = karate.properties['baseUrl'] || 'http://localhost:8080';
  karate.log('Karate baseUrl:', baseUrl);
  return { baseUrl: baseUrl };
}
