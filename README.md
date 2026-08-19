# Sharded Search Engine

Visit the site at: https://telemachus.relzarick.com/

## Setup

- Clone the repo into your linux file system (There will be a big time penalty otherwise).
- Place the dataset into the data folder under the backend dir.
- A mock.js file is provided to test the frontend without querying the server.

Currently designed to only ingest a single .csv file it does not support multiple datasets.

### Dataset Used

- https://www.kaggle.com/datasets/alanvourch/tmdb-movies-daily-updates?resource=download

## How It Works

### Ingestion multi-stage pipeline:

- Parse & Split: The CSV is read and split into two processing lines.

- Store Raw Data: One line saves the complete documents straight into MongoDB for queris.

- Keyword Processing: Extracts key terms from the data to build a fast-lookup index.

- RocksDB Ingestion: Builds a composite key using the token and UUID, storing the posistions.

### Look up ranking algorithm

- The index stores the token mapped to a list of UUIDs and positions of occurence.
- The data allows the system to sort and rank key words with the TF-IDF algorithm with phrase matching/ proximity
  scoring.
- Additonal client-side filtering, such as range filters or string omission, is determined by header types.

## Performance Benchmarks

The dataset tested contained around 1.23 million CSV rows in ~24s.

- Total CSV Parse Time: ~2.4 seconds

- MongoDB Ingestion Speed: ~52,564 rows/sec (1.23M rows)

- Keyword Indexing Speed: ~2,634,615 tokens/sec (61.65M tokens)

| Metric              | Throughput       |
|---------------------|------------------|
| **Total Pipeline**  | ~51.2k RPS       |
| **CSV Parse**       | ~512.5k RPS      |
| **Mongo Operation** | ~56.9k RPS       |
| **Indexing**        | ~2.8M tokens/sec |

## Notes

If I were to rebuild this again, during ingestion, I would use some sort of AI to analyze randomly picked contents of
the data. Using that data and the header to build a short summary describing the contents of the dataset.

This will then be used again during query time, analyzing the user's input against this summary to insert more keywords
into the query for better relevancy. This can also act as a secondary ranking signal.
