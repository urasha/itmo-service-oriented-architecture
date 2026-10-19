window.onload = function () {
  const config = window.SOA_CONFIG || {};
  const flats = config.flatsBaseUrl || window.location.origin;
  const agency = config.agencyBaseUrl;
  // The bundled Swagger UI parses the original YAML; augment the parsed objects only.
  const publicServers = () => ({ statePlugins: { spec: { wrapActions: {
    updateJsonSpec: original => specification => {
      const isAgency = Object.keys(specification.paths || {}).some(path => path.startsWith('/agency/'));
      const base = isAgency ? agency : flats;
      return original({ ...specification, servers: base ? [{ url: base }] : [] });
    }
  } } } });
  window.ui = SwaggerUIBundle({
    urls: [
      {name: 'Flat Collection Service', url: './flats.yaml'},
      {name: 'Agency Service', url: './agency.yaml'}
    ],
    'urls.primaryName': 'Flat Collection Service',
    dom_id: '#swagger-ui',
    deepLinking: true,
    validatorUrl: null,
    supportedSubmitMethods: ['get', 'post', 'put', 'patch', 'delete'],
    tryItOutEnabled: true,
    plugins: [publicServers],
    requestInterceptor: request => {
      if (!request.loadSpec && new URL(request.url, window.location.href).pathname.startsWith('/agency/') && !agency)
        throw new Error('Не задан AGENCY_PUBLIC_BASE_URL в конфигурации Jetty');
      return request;
    },
    docExpansion: 'list',
    defaultModelsExpandDepth: 1,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    layout: 'StandaloneLayout'
  });
};
