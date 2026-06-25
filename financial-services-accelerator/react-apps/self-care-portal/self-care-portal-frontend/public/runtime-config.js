/**
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

window.env = {
    // USE_DEFAULT_CONFIGS: false routes OAuth2 calls to SERVER_URL (the IS).
    // With true, the app uses its own origin as the server which causes a
    // redirect loop when nginx is serving the SPA on a different port than IS.
    USE_DEFAULT_CONFIGS: false,
    // Point to the nginx reverse proxy so all requests stay on the same origin.
    // Nginx will forward /oauth2/, /consentmgr/scp* and /oidc/ to the IS.
    SERVER_URL: 'http://localhost:3000',
    TENANT_DOMAIN: 'carbon.super',
    NUMBER_OF_CONSENTS: 20,
    VERSION: '4.0.0',
    IS_DEV_TOOLS_ENABLE: false
};

