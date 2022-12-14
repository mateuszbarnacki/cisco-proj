package com.project.translator.adapter.out.persistence;

import com.project.translator.application.port.in.LanguageDetails;
import com.project.translator.application.port.in.MessageDetails;
import com.project.translator.application.port.in.TagDetails;
import com.project.translator.domain.exception.OriginalMessageIsNotNullException;
import com.project.translator.domain.exception.OriginalMessageNotInEnglishException;
import com.project.translator.domain.exception.TranslationCannotBeConvertedException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({MessagePersistenceAdapter.class,
        LanguagePersistenceAdapter.class,
        TagPersistenceAdapter.class})
class MessagePersistenceAdapterTest {

    private static final String ENGLISH_LANGUAGE = "English";
    private static final String POLISH_LANGUAGE = "Polish";
    private static final String NOTE_TAG = "Note";
    private static final String MESSAGE_TAG = "Message";
    private static final Long ORIGINAL_MESSAGE_ID = 1L;
    private static final Long TRANSLATION_ID = 2L;
    private static final Long ENGLISH_LANG_ID = 1L;
    private static final Long POLISH_LANG_ID = 2L;
    @Autowired
    private MessagePersistenceAdapter messageAdapter;

    @Autowired
    private LanguagePersistenceAdapter languageAdapter;

    @Autowired
    private TagPersistenceAdapter tagAdapter;

    @Autowired
    private MessageRepository messageRepository;

    @MockBean
    private TranslatorMapper translatorMapper;

    @BeforeAll
    void setup() {
        languageAdapter.createLanguage(createLanguageDetails(ENGLISH_LANGUAGE));
        languageAdapter.createLanguage(createLanguageDetails(POLISH_LANGUAGE));

        tagAdapter.createTag(createTagDetails(NOTE_TAG));
        tagAdapter.createTag(createTagDetails(MESSAGE_TAG));

        messageAdapter.createMessage(createOriginalMessageDetails("Original message", ENGLISH_LANG_ID));
        messageAdapter.createMessage(createTranslationMessageDetails("Message translation"));
    }

    @Test
    void messagesShouldBeCreatedSuccessfully() {
        assertThat(messageRepository.findAll()).hasSize(2);
    }

    @Test
    void originalMessageShouldBeCreatedProperly() {
        final var originalMessage = messageRepository.findById(ORIGINAL_MESSAGE_ID).get();
        assertThat(originalMessage.getOriginalMessage()).isNull();
    }

    @Test
    void translationShouldBeCreatedProperly() {
        final var translation = messageRepository.findById(TRANSLATION_ID).get();
        assertThat(translation.getOriginalMessage().getId()).isEqualTo(ORIGINAL_MESSAGE_ID);
    }

    @Test
    void translationShouldHaveOriginalMessageTags() {
        final var originalMessage = messageRepository.findById(ORIGINAL_MESSAGE_ID).get();
        final var translation = messageRepository.findById(TRANSLATION_ID).get();
        assertEquals(originalMessage.getTags(), translation.getTags());
    }

    @Test
    void shouldThrowWhenCreatingOriginalMessageNotInEnglish() {
        final var messageDetails = createOriginalMessageDetails("Original message in Polish", POLISH_LANG_ID);
        assertThrows(OriginalMessageNotInEnglishException.class, () -> messageAdapter.createMessage(messageDetails));
    }

    @Test
    void originalMessageShouldBeUpdatedProperly() {
        final var originalMessage = messageRepository.findById(ORIGINAL_MESSAGE_ID).get();
        final var content = "Update original message";
        final var updatedMessageDetails = createOriginalMessageDetails(content, ENGLISH_LANG_ID);
        messageAdapter.updateMessage(originalMessage.getId(), updatedMessageDetails);
        final var updatedMessage = messageRepository.findById(ORIGINAL_MESSAGE_ID).get();
        assertEquals(content, updatedMessage.getContent());
    }

    @Test
    void shouldThrowWhenTryingAddOriginalMessage() {
        final var originalMessage = messageRepository.findById(ORIGINAL_MESSAGE_ID).get();
        final var content = "Update original message id in original message";
        final var updatedMessageDetails = new MessageDetails(ORIGINAL_MESSAGE_ID, ENGLISH_LANG_ID, content, List.of(1L, 2L));
        assertThrows(OriginalMessageIsNotNullException.class,
                () -> messageAdapter.updateMessage(originalMessage.getId(), updatedMessageDetails));
    }

    @Test
    void shouldThrowWhenTryingChangeOriginalMessageLang() {
        final var originalMessage = messageRepository.findById(ORIGINAL_MESSAGE_ID).get();
        final var content = "Update original message language to Polish";
        final var updatedMessageDetails = new MessageDetails(null, POLISH_LANG_ID, content, List.of(1L, 2L));
        assertThrows(OriginalMessageNotInEnglishException.class,
                () -> messageAdapter.updateMessage(originalMessage.getId(), updatedMessageDetails));
    }

    @Test
    void shouldThrowWhenTryingConvertToOriginalMessage() {
        final var translation = messageRepository.findById(TRANSLATION_ID).get();
        final var content = "Update translation with on original message id";
        final var updatedMessageDetails = new MessageDetails(null, POLISH_LANG_ID, content, List.of(1L, 2L));
        assertThrows(TranslationCannotBeConvertedException.class,
                () -> messageAdapter.updateMessage(translation.getId(), updatedMessageDetails));
    }

    @Test
    void translationShouldBeUpdatedProperly() {
        final var translation = messageRepository.findById(TRANSLATION_ID).get();
        final var content = "Update translation";
        List<Long> emptyTags = Collections.emptyList();
        final var updatedMessageDetails = new MessageDetails(ORIGINAL_MESSAGE_ID, POLISH_LANG_ID, content, emptyTags);
        messageAdapter.updateMessage(translation.getId(), updatedMessageDetails);
        final var updatedTranslation = messageRepository.findById(TRANSLATION_ID).get();
        final var originalMessage = messageRepository.findById(ORIGINAL_MESSAGE_ID).get();
        assertEquals(content, updatedTranslation.getContent());
        assertEquals(updatedTranslation.getTags(), originalMessage.getTags());
    }

    @Test
    void translationShouldBeDeletedSuccessfully() {
        final var translationContent = "Message translation 2";
        messageAdapter.createMessage(createTranslationMessageDetails(translationContent));
        assertThat(messageRepository.findAll()).hasSize(3);
        final var translation = messageRepository.findByContentContainingIgnoreCase(translationContent).get(0);
        messageAdapter.deleteMessage(translation.getId());
        assertThat(messageRepository.findAll()).hasSize(2);
    }

    @Test
    void originalMessageShouldBeDeletedSuccessfully() {
        final var content = "Original message 2";
        messageAdapter.createMessage(createOriginalMessageDetails(content, ENGLISH_LANG_ID));
        final var originalMessage = messageRepository.findByContentContainingIgnoreCase(content).get(0);
        final var translationContent = "Message translation to delete";
        messageAdapter.createMessage(new MessageDetails(originalMessage.getId(), POLISH_LANG_ID, translationContent, Collections.emptyList()));
        assertThat(messageRepository.findAll()).hasSize(4);
        messageAdapter.deleteMessage(originalMessage.getId());
        assertThat(messageRepository.findAll()).hasSize(2);
    }

    @Test
    void shouldFindAllMessages() {
        assertThat(messageAdapter.getMessages()).hasSize(2);
    }

    private MessageDetails createOriginalMessageDetails(String content, Long language) {
        return new MessageDetails(null, language, content, List.of(1L, 2L));
    }

    private MessageDetails createTranslationMessageDetails(String content) {
        return new MessageDetails(ORIGINAL_MESSAGE_ID, POLISH_LANG_ID, content, List.of(1L));
    }

    private LanguageDetails createLanguageDetails(String language) {
        return new LanguageDetails(language);
    }

    private TagDetails createTagDetails(String tag) {
        return new TagDetails(tag);
    }

}
