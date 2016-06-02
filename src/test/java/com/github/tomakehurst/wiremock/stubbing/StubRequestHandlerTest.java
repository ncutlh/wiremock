/*
 * Copyright (C) 2011 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.tomakehurst.wiremock.stubbing;

import com.github.tomakehurst.wiremock.common.LocalNotifier;
import com.github.tomakehurst.wiremock.common.Notifier;
import com.github.tomakehurst.wiremock.core.StubServer;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.*;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.ArrayList;

import static com.github.tomakehurst.wiremock.http.RequestMethod.GET;
import static com.github.tomakehurst.wiremock.http.Response.response;
import static com.github.tomakehurst.wiremock.testsupport.MockRequestBuilder.aRequest;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.CoreMatchers.containsString;

@RunWith(JMock.class)
public class StubRequestHandlerTest {

	private Mockery context;
	private StubServer stubServer;
	private ResponseRenderer responseRenderer;

	private StubRequestHandler requestHandler;

	@Before
	public void init() {
		context = new Mockery();
		stubServer = context.mock(StubServer.class);
		responseRenderer = context.mock(ResponseRenderer.class);
		requestHandler = new StubRequestHandler(stubServer, responseRenderer);
	}

	@Test
	public void returnsResponseIndicatedByMappings() {
		context.checking(new Expectations() {{
			allowing(stubServer).serveStubFor(with(any(Request.class))); will(returnValue(new ResponseDefinition(200, "Body content")));

			Response response = response().status(200).body("Body content").build();
			allowing(responseRenderer).render(with(any(ResponseDefinition.class))); will(returnValue(response));
		}});

		Request request = aRequest(context)
				.withUrl("/the/required/resource")
				.withMethod(GET)
				.build();
		Response response = requestHandler.handle(request);

		assertThat(response.getStatus(), is(200));
		assertThat(response.getBodyAsString(), is("Body content"));
	}

	@Test
	public void shouldNotifyListenersOnRequest() {
		final Request request = aRequest(context).build();
		final RequestListener listener = context.mock(RequestListener.class);
		requestHandler.addRequestListener(listener);

		context.checking(new Expectations() {{
			allowing(stubServer).serveStubFor(request);
			will(returnValue(ResponseDefinition.notConfigured()));
			one(listener).requestReceived(with(equal(request)), with(any(Response.class)));
			allowing(responseRenderer).render(with(any(ResponseDefinition.class)));
		}});

		requestHandler.handle(request);
	}

	@Test
	public void shouldLogInfoOnRequest() {
		final Request request = aRequest(context)
				.withUrl("/")
				.withMethod(GET)
				.withClientIp("1.2.3.5")
				.build();

		final TestNotifier notifier = new TestNotifier();

		context.checking(new Expectations() {{
			allowing(stubServer).serveStubFor(request);
			will(returnValue(ResponseDefinition.notConfigured()));
			allowing(responseRenderer).render(with(any(ResponseDefinition.class)));
		}});

		LocalNotifier.set(notifier);
		requestHandler.handle(request);
		LocalNotifier.set(null);

		assertThat(notifier.getErrorMessages().isEmpty(), is(true));
		assertThat(notifier.getInfoMessages().size(), is(1));
		assertThat(notifier.getInfoMessages().get(0), containsString("1.2.3.5 - GET /"));
	}

	private class TestNotifier implements Notifier {
		private List<String> info;
		private List<String> error;

		TestNotifier() {
			this.info = new ArrayList<String>();
			this.error = new ArrayList<String>();
		}

		@Override
		public void info(String message) {
			this.info.add(message);
		}

		@Override
		public void error(String message) {
			this.error.add(message);
		}

		@Override
		public void error(String message, Throwable t) {
			this.error.add(message);
		}

		public List<String> getInfoMessages() { return info; }

		public List<String> getErrorMessages() { return error; }
	}
}
