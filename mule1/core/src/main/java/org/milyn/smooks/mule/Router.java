/**
 * Copyright (C) 2008 Maurice Zeijen <maurice@zeijen.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.milyn.smooks.mule;

import static org.mule.config.i18n.MessageFactory.createStaticMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.milyn.Smooks;
import org.milyn.container.ExecutionContext;
import org.milyn.event.report.HtmlReportGenerator;
import org.mule.config.i18n.Message;
import org.mule.routing.outbound.FilteringOutboundRouter;
import org.mule.umo.UMOMessage;
import org.mule.umo.UMOSession;
import org.mule.umo.endpoint.UMOEndpoint;
import org.mule.umo.endpoint.UMOImmutableEndpoint;
import org.mule.umo.lifecycle.InitialisationException;
import org.mule.umo.routing.RoutingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 *
 * @author <a href="mailto:maurice@zeijen.net">Maurice Zeijen</a>
 */
public class Router extends FilteringOutboundRouter {

	private static final Logger log = LoggerFactory.getLogger(Router.class);

	public static final String MESSAGE_PROPERTY_KEY_EXECUTION_CONTEXT = "SmooksExecutionContext";

	private static final long serialVersionUID = 1L;

	/*
	 * Smooks payload processor
	 */
	private SmooksPayloadProcessor smooksPayloadProcessor;

	/*
	 * Smooks instance
	 */
	private Smooks smooks;

	/*
	 * Filename for smooks configuration. Default is smooks-config.xml
	 */
    private String configFile;

    /*
     * If true, then the execution context is set as property on the message
     */
    private boolean executionContextAsMessageProperty = false;

	/*
     * The key name of the execution context message property
     */
    private String executionContextMessagePropertyKey = MESSAGE_PROPERTY_KEY_EXECUTION_CONTEXT;

    /*
     * If true, non serializable attributes from the Smooks ExecutionContext will no be included. Default is true.
     */
	private boolean excludeNonSerializables = true;

	/*
	 * Path where the Smooks Report will be generated.
	 */
	private String reportPath;

    // flag which, if true, makes the splitter honour settings such as remoteSync and
    // synchronous on the endpoint
	private boolean honorSynchronicity = false;

	private boolean initialized = false;

	private Map<String, UMOEndpoint> endpointMap;

	public void initialise() throws InitialisationException
	{
		// Create the Smooks instance
		smooks = createSmooksInstance();

		//	Create the Smooks payload processor
		smooksPayloadProcessor = new SmooksPayloadProcessor( smooks, ResultType.NORESULT );

		initialized = true;
	}

	/**
	 * @return the executionContextAsMessageProperty
	 */
	public boolean isExecutionContextAsMessageProperty() {
		return executionContextAsMessageProperty;
	}

	/**
	 * @return the executionContextMessagePropertyKey
	 */
	public String getExecutionContextMessagePropertyKey() {
		return executionContextMessagePropertyKey;
	}

	/**
	 * @return the excludeNonSerializables
	 */
	public boolean isExcludeNonSerializables() {
		return excludeNonSerializables;
	}

	/**
	 * @return the reportPath
	 */
	public String getReportPath() {
		return reportPath;
	}

	/**
	 * @return the honorSynchronicity
	 */
	public boolean isHonorSynchronicity() {
		return honorSynchronicity;
	}

	public String getConfigFile()
	{
		return configFile;
	}

	public void setConfigFile( final String configFile )
	{
		this.configFile = configFile;

		try {
			//The Initializable interface isn't used for router so we need to initialize the router somewhere
			initialise();
		} catch (InitialisationException e) {
			throw new IllegalStateException("Couldn't initialize the Router", e);
		}
	}

	/**
     * Sets the flag indicating whether the splitter honurs endpoint settings
     *
     * @param honorSynchronicity flag setting
     */
	public void setHonorSynchronicity(boolean honorSynchronicity) {
		this.honorSynchronicity = honorSynchronicity;
	}

    /**
	 * @param setExecutionContextMessageProperty the setExecutionContextMessageProperty to set
	 */
	public void setExecutionContextAsMessageProperty(
			boolean executionContextMessageProperty) {
		this.executionContextAsMessageProperty = executionContextMessageProperty;
	}

	/**
	 * @param executionContextMessagePropertyKey the executionContextMessagePropertyKey to set
	 */
	public void setExecutionContextMessagePropertyKey(
			String executionContextMessagePropertyKey) {

		if ( executionContextMessagePropertyKey == null )
		{
			throw new IllegalArgumentException( "'executionContextMessagePropertyKey' can not be set to null." );
		}
		if ( executionContextMessagePropertyKey.length() == 0 )
		{
			throw new IllegalArgumentException( "'executionContextMessagePropertyKey' can not be set to an empty string." );
		}

		this.executionContextMessagePropertyKey = executionContextMessagePropertyKey;
	}

    /**
     * @param excludeNonSerializables  - If true, non serializable attributes from the Smooks ExecutionContext will no be included. Default is true.
     */
	public void setExcludeNonSerializables( boolean excludeNonSerializables )
	{
		this.excludeNonSerializables = excludeNonSerializables;
	}

	public void setReportPath( final String reportPath )
	{
		this.reportPath = reportPath;
	}


	@Override
	public UMOMessage route(UMOMessage message, UMOSession session, boolean synchronous) throws RoutingException {
		if(!initialized) {
			throw new IllegalStateException("The router is not initialised");
		}


		// Retrieve the payload from the message
		Object payload = message.getPayload();

        //	Create Smooks ExecutionContext.
		final ExecutionContext executionContext = smooks.createExecutionContext();

		// Create the dispatcher which handles the dispatching of messages
		NamedOutboundEndpointMuleDispatcher dispatcher = createDispatcher(executionContext, session, synchronous);

		// make the dispatcher available for Smooks
		executionContext.setAttribute(NamedEndpointMuleDispatcher.SMOOKS_CONTEXT, dispatcher);

		//	Add smooks reporting if configured
		addReportingSupport( message, executionContext );

        //	Use the Smooks PayloadProcessor to execute the routing....
        smooksPayloadProcessor.process( payload, executionContext );

		return null;
	}


	private Smooks createSmooksInstance() throws InitialisationException
	{
		if ( configFile == null )
		{
			final Message errorMsg = createStaticMessage( "'smooksConfigFile' parameter must be specified" );
			throw new InitialisationException( errorMsg, this );
		}

		try
		{
			return new Smooks ( configFile );
		}
		catch ( final IOException e)
		{
			final Message errorMsg = createStaticMessage( "IOException while trying to get smooks instance: " );
			throw new InitialisationException( errorMsg, e, this);
		}
		catch ( final SAXException e)
		{
			final Message errorMsg = createStaticMessage( "SAXException while trying to get smooks instance: " );
			throw new InitialisationException( errorMsg, e, this);
		}
	}


	private NamedOutboundEndpointMuleDispatcher createDispatcher(final ExecutionContext executionContext, final UMOSession muleSession, final  boolean synchronous) {
		initialiseEndpointMap();

		//Create the dispatcher which will dispatch the messages provided by Smooks
		NamedOutboundEndpointMuleDispatcher dispatcher = new NamedOutboundEndpointMuleDispatcher(endpointMap, this, muleSession, executionContext, executionContextAsMessageProperty, executionContextMessagePropertyKey, excludeNonSerializables, honorSynchronicity, synchronous);

		return dispatcher;
	}

	private void addReportingSupport(UMOMessage message, final ExecutionContext executionContext ) throws RoutingException
	{
		if( reportPath != null )
		{
            try
            {
            	log.info( "Using Smooks Reporting. Will generate report in file [" + reportPath  + "]. Do not use in production evironment as this will have negative impact on performance!");
                executionContext.setEventListener( new HtmlReportGenerator( reportPath ) );
            }
            catch ( final IOException e)
            {
    			final Message errorMsg = createStaticMessage( "Failed to create HtmlReportGenerator instance." );
	            throw new RoutingException(errorMsg, message, (UMOImmutableEndpoint) null, e );
            }
        }
	}

	private void initialiseEndpointMap() {
		if(endpointMap == null) {
			synchronized (this) {
				if(endpointMap == null) {
					Map<String, UMOEndpoint> lEndpointMap = new HashMap<String, UMOEndpoint>();

					for(Object e :  getEndpoints()) {
						UMOEndpoint endpoint = (UMOEndpoint) e;

						String name = endpoint.getName();
						if(StringUtils.isEmpty(name)) {
							throw new IllegalArgumentException("The outbound endpoint list may only contain endpoints which have a name");
						}

						lEndpointMap.put(name, endpoint);
					}

					endpointMap = lEndpointMap;
				}
			}
		}
	}
}
