package com.Upwork.api;

import com.xclydes.finance.longboard.models.Token;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class OAuthClient {
    private static final int METHOD_GET     = 1;
    private static final int METHOD_POST    = 2;
    private static final int METHOD_PUT     = 3;
    private static final int METHOD_DELETE  = 4;

    private static final String OVERLOAD_PARAM = "http_method";
    private static final String DATA_FORMAT = "json";
    private static final String UPWORK_BASE_URL = "https://www.upwork.com/";

    private static final String OAUTH_REQUEST_TOKEN_ENDPOINT =
            UPWORK_BASE_URL + "api/auth/v1/oauth/token/request";
    private static final String OAUTH_ACCESS_TOKEN_ENDPOINT =
            UPWORK_BASE_URL + "api/auth/v1/oauth/token/access";
    private static final String OAUTH_AUTHORIZATION_ENDPOINT =
            UPWORK_BASE_URL + "services/api/auth";

    private static OAuthProvider mOAuthProvider;

    private final OAuthConsumer mOAuthConsumer;
    private final Token clientToken;
    private Token accessToken;
    private String entryPoint		= "api";

    /**
     * Constructor
     *
     * */
    public OAuthClient(final Token accessToken) {

        this.clientToken = accessToken;

        this.mOAuthConsumer = new CommonsHttpOAuthConsumer(accessToken.getKey(), accessToken.getSecret());
    }

    public Token getAccessToken() {
        return this.accessToken;
    }

    public Token getClientToken() {
        return clientToken;
    }

    public OAuthConsumer getOAuthConsumer() {
        return mOAuthConsumer;
    }

    /**
     * Get authorization URL, and use provided callback
     *
     * @param   oauthCallback URL, i.e. oauth_callback used in mobile applications
     * @return	URL for authorizing application
     * */
    public String getAuthorizationUrl(String oauthCallback) {
        return _getAuthorizationUrl(oauthCallback);
    }

    /**
     * Get authorization URL
     *
     * @return	URL for authorizing application
     * */
    public String getAuthorizationUrl() {
        return _getAuthorizationUrl("");
    }

    /**
     * Get access token-secret pair
     *
     * @param	verifier OAuth verifier, which was got after authorization
     * @return	Access token-secret pair
     * */
    public Token getAccessTokenSet(final String verifier) {
        final OAuthConsumer oAuthConsumer = this.getOAuthConsumer();
        try {
            getOAuthProvider().retrieveAccessToken(oAuthConsumer, verifier);
        }
        catch (OAuthException e) {
            e.printStackTrace();
        }

        return setTokenWithSecret(oAuthConsumer.getToken(), oAuthConsumer.getTokenSecret());
    }

    /**
     * Setup access token and secret for OAuth client
     *
     * @param	aToken Access token
     * @param	aSecret Access secret
     * @return	Token-secret pair
     * */
    public final Token setTokenWithSecret(final String aToken, final String aSecret) {
        HashMap<String, String> token = new HashMap<>();

//        accessToken	= aToken;
//        accessSecret = aSecret;
        this.accessToken = Token.of(aToken, aSecret);

        this.getOAuthConsumer().setTokenWithSecret(this.accessToken.getKey(), this.accessToken.getSecret());

//        token.put("token", accessToken);
//        token.put("secret", accessSecret);

        return this.accessToken;
    }

    /**
     * Setup entry point for the request(s)
     *
     * @param	ep Entry point
     * */
    public final void setEntryPoint(String ep) {
        this.entryPoint = ep;
    }

    /**
     * Send signed OAuth GET request without parameters
     *
     * @param	url Relative URL
     * @throws JSONException If JSON object is invalid or request was abnormal
     * @return	{@link JSONObject} JSON Object that contains data from response
     * */
    public JSONObject get(String url) throws JSONException {
        return sendGetRequest(url, METHOD_GET, null);
    }

    /**
     * Send signed OAuth GET request
     *
     * @param	url Relative URL
     * @param	params Hash of parameters
     * @throws	JSONException If JSON object is invalid or request was abnormal
     * @return	{@link JSONObject} JSON Object that contains data from response
     * */
    public JSONObject get(String url, HashMap<String, String> params) throws JSONException {
        return sendGetRequest(url, METHOD_GET, params);
    }

    /**
     * Send signed OAuth POST request
     *
     * @param	url Relative URL
     * @param	params Hash of parameters
     * @throws	JSONException If JSON object is invalid or request was abnormal
     * @return	{@link JSONObject} JSON Object that contains data from response
     * */
    public JSONObject post(String url, HashMap<String, String> params) throws JSONException {
        return sendPostRequest(url, METHOD_POST, params);
    }

    /**
     * Send signed OAuth PUT request
     *
     * @param	url Relative URL
     * @throws	JSONException If JSON object is invalid or request was abnormal
     * @return	{@link JSONObject} JSON Object that contains data from response
     * */
    public JSONObject put(String url) throws JSONException {
        return sendPostRequest(url, METHOD_PUT, new HashMap<>());
    }

    /**
     * Send signed OAuth PUT request
     *
     * @param	url Relative URL
     * @param	params Hash of parameters
     * @throws	JSONException If JSON object is invalid or request was abnormal
     * @return	{@link JSONObject} JSON Object that contains data from response
     * */
    public JSONObject put(String url, HashMap<String, String> params) throws JSONException {
        return sendPostRequest(url, METHOD_PUT, params);
    }

    /**
     * Send signed OAuth DELETE request without parameters
     *
     * @param	url Relative URL
     * @throws	JSONException If JSON object is invalid or request was abnormal
     * @return	{@link JSONObject} JSON Object that contains data from response
     * */
    public JSONObject delete(String url) throws JSONException {
        return sendPostRequest(url, METHOD_DELETE, null);
    }

    /**
     * Send signed OAuth DELETE request
     *
     * @param	url Relative URL
     * @param	params Hash of parameters
     * @throws	JSONException If JSON object is invalid or request was abnormal
     * @return	{@link JSONObject} JSON Object that contains data from response
     * */
    public JSONObject delete(String url, HashMap<String, String> params) throws JSONException {
        return sendPostRequest(url, METHOD_DELETE, params);
    }

    /**
     * Get authorization URL, use provided callback URL
     *
     * @param   oauthCallback URL, i.e. oauth_callback
     * @return	URL for authorizing application
     * */
    private String _getAuthorizationUrl(String oauthCallback) {
        String url = null;

        try {
            url = getOAuthProvider().retrieveRequestToken(this.getOAuthConsumer(), oauthCallback);
        }
        catch (OAuthException e) {
            e.printStackTrace();
        }

        return url;
    }

    /**
     * Send signed GET OAuth request
     *
     * @param	url Relative URL
     * @param	type Type of HTTP request (HTTP method)
     * @param	params Hash of parameters
     * @throws	JSONException If JSON object is invalid or request was abnormal
     * @return	{@link JSONObject} JSON Object that contains data from response
     * */
    private JSONObject sendGetRequest(String url, Integer type, HashMap<String, String> params) throws JSONException {
        String fullUrl = getFullUrl(url);
        HttpGet request = new HttpGet(fullUrl);

        if (params != null) {
            URI uri;
            String query = "";
            try {
                URIBuilder uriBuilder = new URIBuilder(request.getURI());

                // encode values and add them to the request
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    // to prevent double encoding, we need to create query string ourself
                    // uriBuilder.addParameter(key, URLEncoder.encode(value).replace("%3B", ";"));
                    query = query + key + "=" + value.replace("&", "&amp;") + "&";
                    // what the hell is going on in java - no adequate way to encode query string
                    // lets temporary replace "&" in the value, to encode it manually later
                }
                // this routine will encode query string
                uriBuilder.setCustomQuery(query);
                uri = uriBuilder.build();

                // re-create request to have validly encoded ampersand
                request = new HttpGet(fullUrl + "?" + uri.getRawQuery().replace("&amp;", "%26"));
            } catch (URISyntaxException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        try {
            this.getOAuthConsumer().sign(request);
        }
        catch (OAuthException e) {
            e.printStackTrace();
        }

        return UpworkRestClient.getJSONObject(request, type);
    }

    /**
     * Send signed POST OAuth request
     *
     * @param	url Relative URL
     * @param	type Type of HTTP request (HTTP method)
     * @param	params Hash of parameters
     * @throws	JSONException If JSON object is invalid or request was abnormal
     * @return	{@link JSONObject} JSON Object that contains data from response
     * */
    private JSONObject sendPostRequest(String url, Integer type, HashMap<String, String> params) throws JSONException {
        String fullUrl = getFullUrl(url);
        HttpPost request = new HttpPost(fullUrl);

        switch(type) {
            case METHOD_PUT:
            case METHOD_DELETE:
                // assign overload value
                String oValue;
                if (type == METHOD_PUT) {
                    oValue = "put";
                } else {
                    oValue = "delete";
                }
                params.put(OVERLOAD_PARAM, oValue);
            case METHOD_POST:
                break;
            default:
                throw new RuntimeException("Wrong http method requested");
        }

        // doing post request using json to avoid issue with urlencoded symbols
        JSONObject json = new JSONObject();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }

        request.setHeader("Content-Type", "application/json");
        try {
            request.setEntity(new StringEntity(json.toString()));
        } catch (UnsupportedEncodingException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        // sign request
        try {
            this.getOAuthConsumer().sign(request);
        }
        catch (OAuthException e) {
            e.printStackTrace();
        }

        return UpworkRestClient.getJSONObject(request, type, params);
    }

    /**
     * Build absolute URL
     *
     * @param	url Relative URL
     * @return	Absolute URL
     * */
    private final String getFullUrl(String url) {
        return UPWORK_BASE_URL + this.entryPoint + url +
                ((this.entryPoint == "api") ? ("." + DATA_FORMAT) : "");
    }

    public static OAuthProvider getOAuthProvider() {
        if(mOAuthProvider == null) {
            mOAuthProvider = new CommonsHttpOAuthProvider(
                    OAUTH_REQUEST_TOKEN_ENDPOINT,
                    OAUTH_ACCESS_TOKEN_ENDPOINT,
                    OAUTH_AUTHORIZATION_ENDPOINT);
        }
        return mOAuthProvider;
    }
}
