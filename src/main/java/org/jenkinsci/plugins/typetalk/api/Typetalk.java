package org.jenkinsci.plugins.typetalk.api;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientCredentialsTokenRequest;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.typetalk.TypetalkNotifier;

import java.io.IOException;
import java.util.Collections;

public class Typetalk {

	private static final String BASE_URL = "https://typetalk.com";
	private static final String TOKEN_SERVER_URL = BASE_URL + "/oauth2/access_token";

	private static final String SCOPE_TOPIC_POST = "topic.post";

	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final JsonFactory JSON_FACTORY = new GsonFactory();

	private Credential createCredential() throws IOException {
		TokenResponse response =
				new ClientCredentialsTokenRequest(HTTP_TRANSPORT, JSON_FACTORY, new GenericUrl(TOKEN_SERVER_URL))
						.setClientAuthentication(new BasicAuthentication(clientId, clientSecret))
						.setScopes(Collections.singletonList(SCOPE_TOPIC_POST))
						.execute();

		return new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
				.setTransport(HTTP_TRANSPORT)
				.setJsonFactory(JSON_FACTORY)
				.setTokenServerUrl(new GenericUrl(TOKEN_SERVER_URL))
				.setClientAuthentication(new BasicAuthentication(clientId, clientSecret))
				.build()
				.setFromTokenResponse(response);
	}

	private HttpRequestFactory createRequestFactory() {
		return HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
			@Override
			public void initialize(HttpRequest request) throws IOException {
				createCredential().initialize(request);
				request.setParser(new JsonObjectParser(JSON_FACTORY));
			}
		});
	}

	private final String clientId;

	private final String clientSecret;

	public Typetalk(String clientId, String clientSecret) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	public static Typetalk createFromName(String name) {
		Jenkins jenkins = Jenkins.getInstanceOrNull();
		if (jenkins != null) {
			TypetalkNotifier.DescriptorImpl descriptor = (TypetalkNotifier.DescriptorImpl) jenkins.getDescriptor(TypetalkNotifier.class);
			if (descriptor != null) {
				TypetalkNotifier.Credential credential = descriptor.getCredential(name);
				if (credential != null) {
					return new Typetalk(credential.getClientId(), credential.getClientSecret().getPlainText());
				}
				throw new IllegalArgumentException("Credential is not found.");
			}
			throw new NullPointerException("Descriptor is null");
		}
		throw new NullPointerException("Jenkins is not started or is stopped");
	}

	/**
	 * Post a message to Typetalk
	 */
	public int postMessage(final Long topicId, final String message, final Long talkId) throws IOException {
		final GenericUrl url = new PostMessageUrl(topicId);
		final MessageEntity entity = new MessageEntity();
		entity.setMessage(message);
		entity.setTalkIds(new Long[]{talkId});

		// If '#' is included in the message, Typetalk will create a tag.
		// Set true to prevent from creating tag.
		// Tags might be created unexpectedly since `#` might be included in the string passed by Jenkins.
		entity.setIgnoreHashtag(true);

		final HttpContent content = new JsonHttpContent(JSON_FACTORY, entity);
		final HttpRequest request = createRequestFactory().buildPostRequest(url, content);
		HttpResponse response = request.execute();
		response.disconnect();

		return response.getStatusCode();
	}

	static class PostMessageUrl extends GenericUrl {

		PostMessageUrl(Long topicId) {
			super(getBaseUrl() + "/api/v1/topics/" + topicId);
		}

		private static String getBaseUrl() {
			String baseUrl = System.getenv("TYPETALK_BASE_URL");
			return StringUtils.isEmpty(baseUrl) ? BASE_URL : baseUrl;
		}

	}

}
