/**
 *
 */
package org.milyn.smooks.mule;

import static org.mule.config.i18n.MessageFactory.createStaticMessage;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.milyn.Smooks;
import org.milyn.container.ExecutionContext;
import org.milyn.container.plugin.PayloadProcessor;
import org.milyn.container.plugin.ResultType;
import org.milyn.event.report.HtmlReportGenerator;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.MuleSession;
import org.mule.api.config.MuleProperties;
import org.mule.api.endpoint.ImmutableEndpoint;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.routing.CouldNotRouteOutboundMessageException;
import org.mule.api.routing.RoutingException;
import org.mule.config.i18n.Message;
import org.mule.routing.outbound.FilteringOutboundRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * @author maurice_zeijen
 *
 */
public class Router extends FilteringOutboundRouter {

	private static final Logger log = LoggerFactory.getLogger(Router.class);

	public static final String MESSAGE_PROPERTY_KEY_EXECUTION_CONTEXT = "SmooksExecutionContext";

	private static final long serialVersionUID = 1L;

	/*
	 * Smooks payload processor
	 */
	private PayloadProcessor payloadProcessor;

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


	@Override
	public void initialise() throws InitialisationException
	{
		//	Create the Smooks instance
		smooks = createSmooksInstance();

		//	Create the Smooks payload processor
		payloadProcessor = new PayloadProcessor( smooks, ResultType.NORESULT );
	}

	public String getConfigFile()
	{
		return configFile;
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


	public void setConfigFile( final String configFile )
	{
		this.configFile = configFile;
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
	public MuleMessage route(MuleMessage message, MuleSession session, boolean synchronous) throws RoutingException {

		// Retrieve the payload from the message
		Object payload = message.getPayload();

        //	Create Smooks ExecutionContext.
		final ExecutionContext executionContext = smooks.createExecutionContext();


		// Create the dispatcher which handles the dispatching of messages
		AbstractMuleDispatcher dispatcher = createDispatcher(executionContext, session, synchronous);

		// make the dispatcher available for Smooks
		executionContext.setAttribute(MuleDispatcher.SMOOKS_CONTEXT, dispatcher);

		//	Add smooks reporting if configured
		addReportingSupport(message, executionContext );

        //	Use the Smooks PayloadProcessor to execute the routing....
        payloadProcessor.process( payload, executionContext );

		return null;
	}

	/**
     * Will return a Map containing only the Serializable objects
     * that exist in the passed-in Map if {@link #excludeNonSerializables} is true.
     *
     * @param smooksAttribuesMap 	- Map containing attributes from the Smooks ExecutionContext
     * @return Map	- Map containing only the Serializable objects from the passed-in map.
     */
    @SuppressWarnings( "unchecked" )
	protected Map getSerializableObjectsMap( final Map smooksAttribuesMap )
	{
    	if ( !excludeNonSerializables ) {
			return smooksAttribuesMap;
		}

		Map smooksExecutionContextMap = new HashMap();

		Set<Map.Entry> s = smooksAttribuesMap.entrySet();
		for (Map.Entry me : s)
		{
			Object value = me.getValue();
			if( value instanceof Serializable )
			{
				smooksExecutionContextMap.put( me.getKey(), value );
			}
		}
		return smooksExecutionContextMap;
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


	@SuppressWarnings("unchecked")
	private AbstractMuleDispatcher createDispatcher(final ExecutionContext executionContext, final MuleSession muleSession, final  boolean synchronous) {

		//Create the dispatcher which will dispatch the messages provided by Smooks
		AbstractMuleDispatcher dispatcher = new AbstractMuleDispatcher(getEndpoints()) {

			@Override
			public void dispatch(OutboundEndpoint endpoint, MuleMessage message) {

				boolean synced = synchronous;
				if (honorSynchronicity)
                {
					synced = endpoint.isSynchronous();
                }


				if(executionContextAsMessageProperty) {
		        	// Set the Smooks Excecution properties on the Mule Message object
		        	message.setProperty(executionContextMessagePropertyKey, getSerializableObjectsMap( executionContext.getAttributes()) );
		        }

				try {

					if (honorSynchronicity)
                    {
                        message.setBooleanProperty(MuleProperties.MULE_REMOTE_SYNC_PROPERTY, endpoint.isRemoteSync());
                    }

					if(synced) {

						//We ignore the result because we can't do anything meaning full with it
						Router.this.send(muleSession, message, endpoint);

					} else {
						Router.this.dispatch(muleSession, message, endpoint);
					}

				} catch (MuleException e) {

					//TODO: Fixme?
					throw new RuntimeException(new CouldNotRouteOutboundMessageException(message, endpoint, e));

				}
			}
		};
		return dispatcher;
	}

	private void addReportingSupport(final MuleMessage message, final ExecutionContext executionContext ) throws RoutingException
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
	            throw new RoutingException(errorMsg, message, (ImmutableEndpoint)null, e);
            }
        }
	}

}
