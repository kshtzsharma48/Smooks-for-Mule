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


import java.io.File;
import java.io.IOException;

import static org.mockito.Mockito.*;

import javax.xml.transform.dom.DOMResult;

import junit.framework.TestCase;
import org.junit.Before;
import org.milyn.io.StreamUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mule.DefaultMuleMessage;
import org.mule.api.MuleContext;
import org.mule.api.MuleMessage;
import org.mule.api.config.MuleConfiguration;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transformer.TransformerException;
import org.mule.api.transport.PropertyScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Unit test for {@link org.milyn.smooks.mule.SmooksTransformer}
 * <p/>
 * The test in the class intentionally only test the configuration and <br>
 * execution of {@link org.milyn.smooks.mule.SmooksTransformer} and not the actual tranformations<br>
 * that Smooks performs as these are covered in the Smooks project.
 *
 * @author <a href="mailto:maurice@zeijen.net">Maurice Zeijen</a>
 *
 */
public class TransformerTest extends TestCase
{
	private final static Logger logger = LoggerFactory.getLogger(TransformerTest.class);

    private SmooksTransformer transformer;

	private final String smooksConfigFile = "/transformer-smooks-config.xml";

	private final String smooksProfiledConfigFile = "/transformer-smooks-config-profiled.xml";

   // @Mock
   // private MuleMessage message;

    @Mock
    private MuleContext muleContext;

    @Mock
    private MuleConfiguration muleConfiguration;

	public void testInitWithoutSmooksConfigFile() throws InitialisationException
	{
		boolean thrown = false;
		try {
			transformer.setConfigFile( null );
			transformer.initialise();
		} catch (InitialisationException e) {
			thrown = true;
		}
		assertTrue("expected InitialisationException to be thrown", thrown);
	}

	public void testIllegalResultType()
	{
		boolean thrown = false;
		try {
			transformer.setConfigFile( smooksConfigFile );
			transformer.setResultType( "badResultType" );
			transformer.initialise();
		} catch (InitialisationException e) {
			thrown = true;
		}
		assertTrue("expected InitialisationException to be thrown", thrown);
	}

	public void testJavaResultBeanId()
	{
		transformer.setConfigFile( smooksConfigFile );
		transformer.setResultType( "JAVA" );
		transformer.setJavaResultBeanId( "beanId" );
		try
		{
			transformer.initialise();
		}
		catch (InitialisationException e)
		{
			fail( "Should not have thrown A InitializationException");
		}
	}

	public void testResultClass()
	{
		transformer.setConfigFile( smooksConfigFile );
		transformer.setResultType( "RESULT" );
		transformer.setResultClass("javax.xml.transform.dom.DOMResult");
		try
		{
			transformer.initialise();
		}
		catch (InitialisationException e)
		{
			logger.error("Initialisation Exception", e);
			fail( "Should not have thrown A InitializationException");
		}
	}

	public void testResultFactoryClass()
	{
		transformer.setConfigFile( smooksConfigFile );
		transformer.setResultType( "RESULT" );
		transformer.setResultFactoryClass("test.DummyResultFactory");
		try
		{
			transformer.initialise();
		}
		catch (InitialisationException e)
		{
			logger.error("Initialisation Exception", e);

			fail( "Should not have thrown A InitializationException");
		}
	}

	public void testNoResultClassConfigurationOnRESULTResultType()
	{
		boolean thrown = false;
		try {
			transformer.setConfigFile( smooksConfigFile );
			transformer.setResultType( "RESULT" );
			transformer.initialise();
		} catch (InitialisationException e) {
			thrown = true;
		}
		assertTrue("expected InitialisationException to be thrown", thrown);
	}

	public void testIllegalResultClassConfigurationOnRESULTResultType()
	{
		boolean thrown = false;
		try {
			transformer.setConfigFile( smooksConfigFile );
			transformer.setResultType( "RESULT" );
			transformer.setResultClass("test.DOESNOTEXIST");
			transformer.initialise();
		} catch (InitialisationException e) {
			thrown = true;
		}
		assertTrue("expected InitialisationException to be thrown", thrown);
	}


	public void testIllegalResultFactoryClassConfigurationOnRESULTResultType()
	{
		boolean thrown = false;
		try {
			transformer.setConfigFile( smooksConfigFile );
			transformer.setResultType( "RESULT" );
			transformer.setResultFactoryClass("test.DOESNOTEXIST");
			transformer.initialise();
		} catch (InitialisationException e) {
			thrown = true;
		}
		assertTrue("expected InitialisationException to be thrown", thrown);
	}

	public void testDoTransformationExecContextAttrNotDefinend() throws Exception {
		testDoTransformation(null, null);
	}

	public void testDoTransformationWithoutExecContextAttr() throws Exception {
		testDoTransformation(false, null);
	}

	public void testDoTransformationWithExecContextAttr() throws Exception {
		testDoTransformation(true, null);
	}

	public void testDoTransformationWithExecContextAttrWithOwnAttrKey() throws Exception {
		testDoTransformation(true, "executionContextSmooks");
	}

	public void testDoTransformation(Boolean setExecuctionContextAsMessageKey, String executionContextMessagePropertyKey) throws Exception
	{

		transformer.setConfigFile( smooksConfigFile );
		transformer.setExcludeNonSerializables( false );
		if(setExecuctionContextAsMessageKey != null) {
			transformer.setExecutionContextAsMessageProperty(setExecuctionContextAsMessageKey);
		}
		if(executionContextMessagePropertyKey != null) {
			transformer.setExecutionContextMessagePropertyKey(executionContextMessagePropertyKey);
		} else {
			executionContextMessagePropertyKey = org.milyn.smooks.mule.Constants.MESSAGE_PROPERTY_KEY_EXECUTION_CONTEXT;
		}

		transformer.initialise();

		byte[] inputMessage = readInputMessage();

        MuleMessage message = new DefaultMuleMessage(inputMessage, muleContext);

		Object transformedObject = transformer.transform( message );

        transformer.dispose();

		assertNotNull ( transformedObject );

		Object attributes = message.getOutboundProperty(executionContextMessagePropertyKey);

		if(setExecuctionContextAsMessageKey != null && setExecuctionContextAsMessageKey) {
			assertNotNull( attributes );
		} else {
			assertNull( attributes );
		}


	}

	public void testDoTransformationWithSmooksReportGeneration() throws InitialisationException, TransformerException
	{
		File reportFile = new File ( "target" + File.separator + "smooks-report.html" );
		transformer.setConfigFile( smooksConfigFile );
		transformer.setReportPath( reportFile.getAbsolutePath() );
		transformer.initialise();
		byte[] inputMessage = readInputMessage();
		try
		{
    		Object transformedObject = transformer.transform( inputMessage );

            transformer.dispose();

    		assertNotNull ( transformedObject );
			assertTrue( reportFile.exists() );
		}
		finally
		{
			if ( reportFile.exists() )
			{
				reportFile.delete();
			}
		}
	}

	public void testDoTransformationWithResultClassDefinend() throws TransformerException, InitialisationException
	{
		transformer.setConfigFile( smooksConfigFile );
		transformer.setResultType("RESULT");
		transformer.setResultClass("javax.xml.transform.dom.DOMResult");
		transformer.initialise();
		byte[] inputMessage = readInputMessage();

		Object transformedObject = transformer.transform( inputMessage );

        transformer.dispose();

		assertNotNull ( transformedObject );
		assertTrue("transformed Object not a DOMResult", transformedObject instanceof DOMResult);
	}

	public void testDoTransformationWithResultFactoryClassDefinend() throws TransformerException, InitialisationException
	{
		transformer.setConfigFile( smooksConfigFile );
		transformer.setResultType( "RESULT" );
		transformer.setResultFactoryClass("test.DummyResultFactory");
		transformer.initialise();
		byte[] inputMessage = readInputMessage();

		Object transformedObject = transformer.transform( inputMessage );

        transformer.dispose();

		assertNotNull ( transformedObject );
		assertTrue("transformed Object not a DOMResult", transformedObject instanceof DOMResult);
	}


	public void testDoTransformationWithProfileInConfig() throws TransformerException, InitialisationException
	{
		transformer.setConfigFile( smooksProfiledConfigFile );
		transformer.setProfile("profile1");
		transformer.setResultType("STRING");
		transformer.initialise();

		byte[] inputMessage = readInputMessage();

		Object transformedObject = transformer.transform( inputMessage );

        transformer.dispose();

		assertNotNull ( transformedObject );
		assertTrue("transformed Object not a String", transformedObject instanceof String);
		assertTrue("result doesn't contain right xml", transformedObject.toString().contains("<yyy></yyy>"));
	}

	public void testDoTransformationWithProfileInMessage() throws TransformerException, InitialisationException
	{
		final String messagePropertyProfileKey = "smooksMessageProfile";

		transformer.setConfigFile( smooksProfiledConfigFile );
		transformer.setResultType("STRING");
		transformer.setProfileMessagePropertyKey(messagePropertyProfileKey);
		transformer.initialise();

		byte[] inputMessage = readInputMessage();

        MuleMessage message = new DefaultMuleMessage(inputMessage, muleContext);
        message.setProperty(messagePropertyProfileKey, "profile2", PropertyScope.INBOUND);

		Object transformedObject = transformer.transform( message );

        transformer.dispose();

		assertNotNull ( transformedObject );
		assertTrue("transformed Object not a String", transformedObject instanceof String);
		assertTrue("result doesn't contain right xml", transformedObject.toString().contains("<zzz></zzz>"));
	}


	public void testDoTransformationWithProfileInConfigAndMessageMessage() throws TransformerException, InitialisationException
	{
		final String messagePropertyProfileKey = "smooksMessageProfile";

		transformer.setConfigFile( smooksProfiledConfigFile );
		transformer.setResultType("STRING");
		transformer.setProfile("profile1");
		transformer.setProfileMessagePropertyKey(messagePropertyProfileKey);
		transformer.initialise();

		byte[] inputMessage = readInputMessage();

		MuleMessage message = new DefaultMuleMessage(inputMessage, muleContext);
        message.setProperty(messagePropertyProfileKey, "profile2", PropertyScope.INBOUND);


		Object transformedObject = transformer.transform( message );

        transformer.dispose();

		assertNotNull ( transformedObject );
		assertTrue("transformed Object not a String", transformedObject instanceof String);
		assertTrue("result doesn't contain right xml", transformedObject.toString().contains("<zzz></zzz>"));
	}

	@Override
    @Before
	public void setUp() throws Exception
	{
        MockitoAnnotations.initMocks(this);

        when(muleContext.getConfiguration()).thenReturn(muleConfiguration);
        when(muleConfiguration.isAutoWrapMessageAwareTransform()).thenReturn(true);

        transformer = new SmooksTransformer();
        transformer.setMuleContext(muleContext);

	}

	//	private

	private static byte[] readInputMessage()
	{
        try
        {
            return StreamUtils.readStream( TransformerTest.class.getResourceAsStream( "/transformer-input-message.xml"));
        }
        catch (IOException e)
        {
        	e.printStackTrace();
            return "<no-message/>".getBytes();
        }
    }
}
