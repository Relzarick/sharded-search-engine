# Sharded Search Engine

Visit the site at: https://telemachus.relzarick.com/

## Setup

- Clone the repo into your linux file system (There will be a big time penalty otherwise).
- Place the dataset into the data folder under the backend dir.
- A mock.js file is provided to test the frontend without querying the server.

Currently designed to only ingest a single .csv file it does not support multiple datasets. The .csv file may be deleted
after the initial ingestion.

### Dataset Used

- https://www.kaggle.com/datasets/alanvourch/tmdb-movies-daily-updates?resource=download

## How It Works

### Ingestion multi-stage pipeline:

- Parse & Split: The CSV is read and split into two processing lines.

- Store Raw Data: One line saves the complete documents straight into MongoDB for queris.

- Keyword Processing: Extracts key terms from the data to build a fast-lookup index.

- Redis Ingestion: Pushes those keywords into Redis, mapping each token to its document ID for quick lookups.

### Look up ranking algorithm

- The index stores the token mapped to a list of UUIDs and positions of occurence.
- The data allows the system to sort and rank key words with the TF-IDF algorithm with phrase matching/ proximity
  scoring.
- Additonal client-side filtering, such as range filters or string omission, is determined by header types.

## Performance Benchmarks

The dataset tested contained around 1.23 million CSV rows in ~60.2s.

- Total CSV Parse Time: ~2.6 seconds

- MongoDB Ingestion Speed: ~20,440 rows/sec (1.23M rows)

- Keyword Indexing Speed: ~1,023,851 tokens/sec (61.65M tokens)

- Redis Ingestion Speed: ~365,300 commands/sec (61.65M tokens)

| Metric              | Throughput        |
| ------------------- | ----------------- |
| **Total Pipeline**  | ~20.4k RPS        |
| **CSV Parse**       | ~475k RPS         |
| **Mongo Operation** | ~20.4k RPS        |
| **Indexing**        | ~1.02M tokens/sec |
| **Redis Commands**  | ~210.6k cmds/sec  |

## Notes

Using Redis for the inverted index was the wrong choice. I underestimated the sheer volume of write commands.

The better alternative would have been to use RocksDB instead for the index, and configure Redis to hold the token → UUID list for querying instead.

During ingestion, the system should use some sort of AI to analyze random contents of the data, building a short summary of the dataset for use at query time.

This can then be used with another AI during query time, analyzing the user's input against this summary to insert more
keywords into the query for better relevancy. This can also act as a secondary ranking signal.
