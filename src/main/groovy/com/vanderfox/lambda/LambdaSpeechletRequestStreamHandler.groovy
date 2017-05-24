package com.vanderfox.lambda

import com.amazon.speech.speechlet.lambda.SpeechletRequestStreamHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created by Ryan Vanderwerf and Lee Fox on 3/18/16.
 */
public final class LambdaSpeechletRequestStreamHandler extends SpeechletRequestStreamHandler {
    private  static final Logger log = LoggerFactory.getLogger(LambdaSpeechletRequestStreamHandler.class)
    private static final Set<String> supportedApplicationIds = new HashSet<String>()
    static {
        /*
         * This Id can be found on https://developer.amazon.com/edw/home.html#/ "Edit" the relevant
         * Alexa Skill and put the relevant Application Ids in this Set.
         */
        final Properties properties = new Properties()
        try {
            InputStream stream = LambdaSpeechlet.class.getClassLoader()getResourceAsStream("speechlet.properties")
            properties.load(stream)

            def property = properties.getProperty("awsApplicationId")
            log.info("Loading app ids: ${property}")
            def appIds = property.split(",")
            appIds.each { appId ->
                log.info("loading app id ${appId}")
                supportedApplicationIds.add(appId)
            }

        } catch (e) {
            log.error("Unable to aws application id. Please set up a springSocial.properties")
        }

    }


    public LambdaSpeechletRequestStreamHandler() {
        super(new LambdaSpeechlet(), supportedApplicationIds)
    }


}

