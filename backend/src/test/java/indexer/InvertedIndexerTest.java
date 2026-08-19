package indexer;

import etl.QueueItem;
import indexer.tokenizer.StemTokenization;
import org.bson.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class InvertedIndexerTest {
    private InvertedIndexer indexer;
    private final BlockingQueue<QueueItem> indexerQueue = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() {
        indexer = new InvertedIndexer(new StemTokenization());
    }

    @Test
    void tokenizeToIndexTestOutput() throws InterruptedException {
        indexer.tokenizeToQueue(testData(), indexerQueue);
        assertFalse(indexerQueue.isEmpty());
    }

    @Test
    void tokenizeKeyWordsTest() {
        String input = "Chhota Bheem and the Shinobi Secret";
        List<String> tokens = indexer.tokenizeKeyWords(input);

        assertNotNull(tokens, "Tokens set should not be null");
        assertFalse(tokens.isEmpty(), "Tokens set should not be empty for valid input");
    }

    QueueItem.DocumentBatch testData() {
        List<BsonDocument> docs = new ArrayList<>();

        docs.add(new BsonDocument()
                .append("_id", new BsonBinary(UUID.fromString("019f72bf-c76f-75d1-8119-12394b6d413c")))
                .append("id", new BsonInt32(852962))
                .append("title", new BsonString("Birth of a Monster"))
                .append("vote_average", new BsonDouble(0.0))
                .append("vote_count", new BsonDouble(0.0))
                .append("status", new BsonString("Released"))
                .append("release_date", new BsonString("2017-01-01"))
                .append("revenue", new BsonDouble(0.0))
                .append("runtime", new BsonDouble(53.0))
                .append("budget", new BsonDouble(0.0))
                .append("imdb_id", new BsonString(""))
                .append("original_language", new BsonString("en"))
                .append("original_title", new BsonString("Fabrication d'un monstre"))
                .append("overview", new BsonString("For the past 12 years, journalist Paul Moreira has travelled extensively in Iraq. In this film, he goes in search of the men he filmed back in 2003 at the very beginning of the American occupation. Through their stories, and by tracing the roots of ISIS to the arrival of Abu Mousab Al-Zarqawi and America's handling of the resistance, he tells the story of how Iraq became such a fractured nation."))
                .append("popularity", new BsonDouble(0.129))
                .append("tagline", new BsonString(""))
                .append("genres", new BsonString("Documentary, War"))
                .append("production_companies", new BsonString(""))
                .append("production_countries", new BsonString(""))
                .append("spoken_languages", new BsonString("English"))
                .append("cast", new BsonString(""))
                .append("director", new BsonString("Paul Moreira"))
                .append("director_of_photography", new BsonString(""))
                .append("writers", new BsonString(""))
                .append("producers", new BsonString("Luc Hermann"))
                .append("music_composer", new BsonString(""))
                .append("imdb_rating", org.bson.BsonNull.VALUE)
                .append("imdb_votes", org.bson.BsonNull.VALUE)
                .append("poster_path", new BsonString("/haLQouAukGOWs113RogKU56d77y.jpg"))
        );

        docs.add(new BsonDocument()
                .append("_id", new BsonBinary(UUID.fromString("019f72bf-c770-755e-8cfd-4357fc0bf375")))
                .append("id", new BsonInt32(852963))
                .append("title", new BsonString("Chhota Bheem: The Crown of Valhalla"))
                .append("vote_average", new BsonDouble(10.0))
                .append("vote_count", new BsonDouble(1.0))
                .append("status", new BsonString("Released"))
                .append("release_date", new BsonString("2013-05-01"))
                .append("revenue", new BsonDouble(0.0))
                .append("runtime", new BsonDouble(69.0))
                .append("budget", new BsonDouble(0.0))
                .append("imdb_id", new BsonString("tt6442766"))
                .append("original_language", new BsonString("hi"))
                .append("original_title", new BsonString("Chhota Bheem and the Crown of Valhalla"))
                .append("overview", new BsonString("To save the kingdom of Valhalla from invasion by Vikings, Bheem is gathering his army of friends, who are ready to put up a formidable fight!"))
                .append("popularity", new BsonDouble(0.4716))
                .append("tagline", new BsonString(""))
                .append("genres", new BsonString("Animation"))
                .append("production_companies", new BsonString("Green Gold Animation"))
                .append("production_countries", new BsonString("India"))
                .append("spoken_languages", new BsonString("हिन्दी"))
                .append("cast", new BsonString("Vatsal Dubey, Rajesh Kava, Julie Tejwani, Jigna Bhardwaj, Rupa Bhimani"))
                .append("director", new BsonString("Rajiv Chilaka"))
                .append("director_of_photography", new BsonString(""))
                .append("writers", new BsonString(""))
                .append("producers", new BsonString(""))
                .append("music_composer", new BsonString(""))
                .append("imdb_rating", new BsonDouble(8.1))
                .append("imdb_votes", new BsonDouble(91.0))
                .append("poster_path", new BsonString("/rAnp8d4kCKpec7bScxC2m6DHujo.jpg"))
        );

        // 3. Nezlob, Kristino
        docs.add(new BsonDocument()
                .append("_id", new BsonBinary(UUID.fromString("019f72bf-c770-755e-8cfd-4357fc0bf376")))
                .append("id", new BsonInt32(852964))
                .append("title", new BsonString("Nezlob, Kristino"))
                .append("vote_average", new BsonDouble(4.0))
                .append("vote_count", new BsonDouble(1.0))
                .append("status", new BsonString("Released"))
                .append("release_date", new BsonString("1956-08-10"))
                .append("revenue", new BsonDouble(0.0))
                .append("runtime", new BsonDouble(0.0))
                .append("budget", new BsonDouble(0.0))
                .append("imdb_id", new BsonString("tt0247576"))
                .append("original_language", new BsonString("cs"))
                .append("original_title", new BsonString("Nezlob, Kristino"))
                .append("overview", new BsonString(""))
                .append("popularity", new BsonDouble(0.0404))
                .append("tagline", new BsonString(""))
                .append("genres", new BsonString("Comedy"))
                .append("production_companies", new BsonString("Studio hraných filmů"))
                .append("production_countries", new BsonString("Czechoslovakia"))
                .append("spoken_languages", new BsonString("Český"))
                .append("cast", new BsonString("Svatopluk Beneš, Antonín Rýdl, Vladimír Pucholt, Jiří Němeček, Elena Hálková, Gabriela Bártlová-Buddeusová, Jiří Steimar, Antonín Hardt, František Filipovský, Václav Postránecký, Ludmila Vendlová, Zdeňka Baldová, Jiří Dohnal, Miloš Nesvadba, Karel Höger, Bohuš Záhorský, Drahomíra Fialková, Miloš Nedbal, Miloš Vavruška, Zdenka Procházková, Rudolf Deyl"))
                .append("director", new BsonString("Vladimír Čech"))
                .append("director_of_photography", new BsonString("Rudolf Stahl"))
                .append("writers", new BsonString("Vladimír Neff, Vlasta Petrovičová"))
                .append("producers", new BsonString(""))
                .append("music_composer", new BsonString("Dalibor C. Vačkář"))
                .append("imdb_rating", new BsonDouble(4.7))
                .append("imdb_votes", new BsonDouble(13.0))
                .append("poster_path", new BsonString(""))
        );

        // 4. Chhota Bheem and the Shinobi Secret
        docs.add(new BsonDocument()
                .append("_id", new BsonBinary(UUID.fromString("019f72bf-c770-755e-8cfd-4357fc0bf377")))
                .append("id", new BsonInt32(852965))
                .append("title", new BsonString("Chhota Bheem and the Shinobi Secret"))
                .append("vote_average", new BsonDouble(0.0))
                .append("vote_count", new BsonDouble(0.0))
                .append("status", new BsonString("Released"))
                .append("release_date", new BsonString("2013-11-03"))
                .append("revenue", new BsonDouble(0.0))
                .append("runtime", new BsonDouble(63.0))
                .append("budget", new BsonDouble(0.0))
                .append("imdb_id", new BsonString("tt6417830"))
                .append("original_language", new BsonString("hi"))
                .append("original_title", new BsonString("Chhota Bheem and the Shinobi Secret"))
                .append("overview", new BsonString("After learning of a samurai village under threat by their own emperor, Bheem sets off for Japan to offer his help."))
                .append("popularity", new BsonDouble(0.2885))
                .append("tagline", new BsonString(""))
                .append("genres", new BsonString("Adventure, Animation, Comedy"))
                .append("production_companies", new BsonString(""))
                .append("production_countries", new BsonString("India"))
                .append("spoken_languages", new BsonString(""))
                .append("cast", new BsonString(""))
                .append("director", new BsonString("Rajiv Chilaka"))
                .append("director_of_photography", new BsonString(""))
                .append("writers", new BsonString("Darsana Radhakrishnan"))
                .append("producers", new BsonString(""))
                .append("music_composer", new BsonString(""))
                .append("imdb_rating", new BsonDouble(7.9))
                .append("imdb_votes", new BsonDouble(87.0))
                .append("poster_path", new BsonString("/fmt05bmw014lPlZEkSztupQW3uI.jpg"))
        );

        // 5. Stories of the Subconscious Mind
        docs.add(new BsonDocument()
                .append("_id", new BsonBinary(UUID.fromString("019f72bf-c770-755e-8cfd-4357fc0bf378")))
                .append("id", new BsonInt32(852966))
                .append("title", new BsonString("Stories of the Subconscious Mind"))
                .append("vote_average", new BsonDouble(6.0))
                .append("vote_count", new BsonDouble(1.0))
                .append("status", new BsonString("Released"))
                .append("release_date", new BsonString("2018-05-10"))
                .append("revenue", new BsonDouble(0.0))
                .append("runtime", new BsonDouble(10.0))
                .append("budget", new BsonDouble(0.0))
                .append("imdb_id", new BsonString("tt8244842"))
                .append("original_language", new BsonString("en"))
                .append("original_title", new BsonString("Stories of the Subconscious Mind"))
                .append("overview", new BsonString("Psychiatrist Alice Davenport has the unique ability to enter people's subconscious minds. When Carter Brooks, a suicidal young man, enters her office, she must go inside his head to fight his inner demon before it kills him."))
                .append("popularity", new BsonDouble(0.6))
                .append("tagline", new BsonString(""))
                .append("genres", new BsonString("Horror"))
                .append("production_companies", new BsonString(""))
                .append("production_countries", new BsonString("United Kingdom"))
                .append("spoken_languages", new BsonString("English"))
                .append("cast", new BsonString("George Nettleton, Bethan Nash"))
                .append("director", new BsonString("Curt Dennis"))
                .append("director_of_photography", new BsonString(""))
                .append("writers", new BsonString("Curt Dennis"))
                .append("producers", new BsonString("Scott Dance, Max Mir"))
                .append("music_composer", new BsonString(""))
                .append("imdb_rating", new BsonDouble(7.0))
                .append("imdb_votes", new BsonDouble(36.0))
                .append("poster_path", new BsonString("/uemRTRhoLewcWOXxqvKBA6dnv7B.jpg"))
        );

        return new QueueItem.DocumentBatch(docs);
    }

}