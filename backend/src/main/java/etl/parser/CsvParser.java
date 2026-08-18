package etl.parser;

import bootstrap.FileLoader;
import de.siegmar.fastcsv.reader.CsvIndex;
import de.siegmar.fastcsv.reader.CsvRecord;
import de.siegmar.fastcsv.reader.IndexedCsvReader;
import etl.QueueItem;
import org.bson.BsonDocument;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;

public final class CsvParser {
    private final static Path PATH = FileLoader.getSource();

    private final CsvIndex index;
    private final int totalPages;
    private final String[] headers;

    private static final int CAPACITY = 5000;

    /**
     *
     * @throws IOException if path does not exist
     */
    public CsvParser() throws IOException {
        try (IndexedCsvReader<CsvRecord> reader = IndexedCsvReader.builder().pageSize(CAPACITY).ofCsvRecord(PATH)) {
            index = reader.getIndex();
            totalPages = index.pages().size();

            List<CsvRecord> firstPage = reader.readPage(0);

            if (firstPage.isEmpty())
                throw new NoSuchElementException("CSV is empty.");

            headers = firstPage.getFirst().getFields().toArray(new String[0]);
        }
    }

    /**
     * @param threadIndex in the current thread.
     * @param threadCount is threads assigned to parsing.
     * @return the page range for reader.
     */
    public int[] getPageRange(int threadIndex, int threadCount) {
        int base = totalPages / threadCount;

        int start = threadIndex * base;
        int end = (threadIndex == threadCount - 1) ? totalPages : start + base;

        return new int[]{start, end};
    }

    /**
     * This batches documents into chunks of 5k.
     *
     * @throws IOException If path does not exist
     */
    public void parseDataTo(BlockingQueue<QueueItem> queue1, BlockingQueue<QueueItem> queue2, int start, int end) throws IOException, InterruptedException {
        try (IndexedCsvReader<BsonDocument> reader = IndexedCsvReader.builder().index(index).pageSize(CAPACITY).build(new BsonDocHandler(headers), PATH)) {
            for (int i = start; i < end; i++) { // Page loop
                List<BsonDocument> batch = reader.readPage(i);

                // Strip header row from the first page
                if (i == 0 && !batch.isEmpty())
                    batch = batch.subList(1, batch.size());

                if (!batch.isEmpty()) {
                    queue1.put(new QueueItem.DocumentBatch(batch));
                    queue2.put(new QueueItem.DocumentBatch(batch));
                }
            }
        }
    }

}