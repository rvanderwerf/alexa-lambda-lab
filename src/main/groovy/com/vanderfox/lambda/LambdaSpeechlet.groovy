package com.vanderfox.lambda

import com.amazon.speech.slu.Intent
import com.amazon.speech.slu.Slot
import com.amazon.speech.speechlet.IntentRequest
import com.amazon.speech.speechlet.LaunchRequest
import com.amazon.speech.speechlet.Session
import com.amazon.speech.speechlet.SessionEndedRequest
import com.amazon.speech.speechlet.SessionStartedRequest
import com.amazon.speech.speechlet.Speechlet
import com.amazon.speech.speechlet.SpeechletException
import com.amazon.speech.speechlet.SpeechletResponse
import com.amazon.speech.ui.PlainTextOutputSpeech
import com.amazon.speech.ui.Reprompt
import com.amazon.speech.ui.SimpleCard
import com.amazon.speech.ui.SsmlOutputSpeech
import groovy.transform.CompileStatic
import com.vanderfox.lambda.question.Question
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.amazonaws.services.dynamodbv2.model.ScanResult
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;


/**
 * This app shows how to connect to lambda with Spring Social, Groovy, and Alexa.
 * @author Lee Fox and Ryan Vanderwerf
 */
@CompileStatic
public class LambdaSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(LambdaSpeechlet.class)

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        LinkedHashMap<String, Question> askedQuestions = new LinkedHashMap()
        session.setAttribute("askedQuestions", askedQuestions)
        session.setAttribute("questionCounter", 1)
        session.setAttribute("score", 0)
        //initializeComponents(session)
        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
            throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        getWelcomeResponse(session)
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session)
            throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        Intent intent = request.getIntent()
        Slot languageChoice = intent.getSlot("languageChoice")
        String intentName = (intent != null) ? intent.getName() : null
        switch (intentName) {
            case "AnswerIntent":
                getAnswer(intent.getSlot("Answer"), session)
                break
            case "DontKnowIntent":
                processAnswer(session, 5)
                break
            case "AMAZON.HelpIntent":
                getHelpResponse(session)
                break
            case "AMAZON.CancelIntent":
                sayGoodbye()
                break
            case "AMAZON.RepeatIntent":
                repeatQuestion(session)
                break
            case "AMAZON.StopIntent":
                sayGoodbye()
                break
            default:
                didNotUnderstand()
                break
        }
    }

    private SpeechletResponse didNotUnderstand() {
        String speechText = "I'm sorry.  I didn't understand what you said.  Say help me for help.";
        askResponse(speechText, speechText)
    }

    private SpeechletResponse sayGoodbye() {
        String speechText = "OK.  I'm going to stop the game now.";
        tellResponse(speechText, speechText)
    }

    private SpeechletResponse repeatQuestion(final Session session) {
        Question question = (Question) session.getAttribute("lastQuestionAsked")
        String speechText = ""

        speechText += "\n"
        speechText += question.getQuestion() + "\n"
        String[] options = question.getOptions()
        int index = 1
        for(String option: options) {
            speechText += (index++) + "\n\n\n\n" + option + "\n\n\n"
        }
        askResponse(speechText, speechText)

    }

    private SpeechletResponse getHelpResponse(Session session) {
        String speechText = ""
        speechText = "You can say stop or cancel to end the game at any time.  If you need a question repeated, say repeat question.";
        askResponse(speechText, speechText)
    }

    private SpeechletResponse getAnswer(Slot query, final Session session) {

        def speechText

        int guessedAnswer = Integer.parseInt(query.getValue()) - 1
        log.info("Guessed answer is:  " + query.getValue())

        return processAnswer(session, guessedAnswer)
    }

    private SpeechletResponse processAnswer(Session session, int guessedAnswer) {
        def speechText
        Question question = (Question) session.getAttribute("lastQuestionAsked")
        def answer = question.getAnswer()
        log.info("correct answer is:  " + answer)
        int questionCounter = Integer.parseInt((String) session.getAttribute("questionCounter"))

        questionCounter = decrementQuestionCounter(session)

        if (guessedAnswer == answer) {
            speechText = "You got it right."
            int score = (Integer) session.getAttribute("score")
            score++
            session.setAttribute("score", score)
        } else {
            speechText = "You got it wrong."
        }

        log.info("questionCounter:  " + questionCounter)

        if (questionCounter > 0) {
            session.setAttribute("state", "askQuestion")
            speechText = getNextQuestion(session, speechText);
            return askResponse(speechText, speechText)
        } else {
            int score = (Integer) session.getAttribute("score")
            speechText += "\n\nYou answered ${score} questions correctly."
            return tellResponse(speechText, speechText)
        }
    }

    private int decrementQuestionCounter(Session session) {
        int questionCounter = (int) session.getAttribute("questionCounter")
        questionCounter--
        session.setAttribute("questionCounter", questionCounter)
        questionCounter

    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
        // any cleanup logic goes here
    }

    private SpeechletResponse lambdaResponse(final Session session, Slot languageChoice) {
        GString speechText = "Cool.  My favorite language is ${languageChoice.value} as well."
        tellResponse(speechText.toString(), speechText.toString())
    }

    private SpeechletResponse getWelcomeResponse(final Session session) {
        String speechText = "Let's get started with a question.\n\n"
        speechText += askQuestion(session)
        askResponse(speechText, speechText)
    }

    private SpeechletResponse askResponse(String cardText, String speechText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard()
        card.setTitle("Hello World")
        card.setContent(cardText)

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech()
        speech.setText(speechText)

        // Create reprompt
        Reprompt reprompt = new Reprompt()
        reprompt.setOutputSpeech(speech)

        SpeechletResponse.newAskResponse(speech, reprompt, card)
    }

    private SpeechletResponse tellResponse(String cardText, String speechText) {
        // Create the Simple card content.
        SimpleCard card = new SimpleCard()
        card.setTitle("Lambda Lab")
        card.setContent(cardText)

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech()
        speech.setText(speechText)

        // Create reprompt
        Reprompt reprompt = new Reprompt()
        reprompt.setOutputSpeech(speech)

        SpeechletResponse.newTellResponse(speech, card)
    }

    private String askQuestion(final Session session) {
        String speechText = ""
        Question question = getRandomQuestion(session)
        speechText += "\n"
        speechText += question.getQuestion() + "\n"
        String[] options = question.getOptions()
        int index = 1
        for(String option: options) {
            speechText += (index++) + "\n\n\n\n" + option + "\n\n\n"
        }
        session.setAttribute("lastQuestionAsked", question)
        speechText
    }

    private Question getRandomQuestion(Session session) {
        AmazonDynamoDBClient amazonDynamoDBClient;
        amazonDynamoDBClient = new AmazonDynamoDBClient();
        ScanRequest req = new ScanRequest();
        req.setTableName("HeroQuiz");
        ScanResult result = amazonDynamoDBClient.scan(req)
        List quizItems = result.items
        int tableRowCount = quizItems.size()
        int questionIndex = (new Random().nextInt() % tableRowCount).abs()
        log.info("The question index is:  " + questionIndex)
        Question question = getQuestion(questionIndex)
        question
    }

    private String getNextQuestion(Session session, String speechText) {
        Question question = getRandomUnaskedQuestion(session)
        session.setAttribute("lastQuestionAsked", question)

        speechText += "\n"
        speechText += question.getQuestion() + "\n"
        String[] options = question.getOptions()
        int index = 1
        for(String option: options) {
            speechText += (index++) + "\n\n\n\n" + option + "\n\n\n"
        }
        speechText

    }


    private Question getRandomUnaskedQuestion(Session session) {
        LinkedHashMap<String, Question> askedQuestions = (LinkedHashMap) session.getAttribute("askedQuestions")
        Question question = getRandomQuestion(session)
        while(askedQuestions.get(question.getQuestion()) != null) {
            question = getRandomQuestion(session)
        }
        askedQuestions.put(question.getQuestion(), question)
        session.setAttribute("askedQuestions", askedQuestions)
        question
    }

    private Question getQuestion(int questionIndex) {
        DynamoDB dynamoDB = new DynamoDB(new AmazonDynamoDBClient())
        Table table = dynamoDB.getTable("HeroQuiz")
        Item item = table.getItem("Id", questionIndex)
        def questionText = item.getString("Question")
        def questionAnswer = item.getInt("answer")
        def options = new String[4]
        options[0] = item.getString("option1")
        options[1] = item.getString("option2")
        options[2] = item.getString("option3")
        options[3] = item.getString("option4")
        Question question = new Question()
        question.setQuestion(questionText)
        question.setOptions(options)
        question.setAnswer(questionAnswer - 1)
        question.setIndex(questionIndex)
        log.info("question retrieved:  " + question.getIndex())
        log.info("question retrieved:  " + question.getQuestion())
        log.info("question retrieved:  " + question.getAnswer())
        log.info("question retrieved:  " + question.getOptions().length)
        question
    }


}
