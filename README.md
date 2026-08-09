# Distributed Search Engine

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

### Ingestion is processed through a multi-stage pipeline:

- Parse & Split: The CSV is read and split into two processing lines.

- Store Raw Data: One line saves the complete documents straight into MongoDB for queris.

- Keyword Processing: Extracts key terms from the data to build a fast-lookup index.

- Redis Ingestion: Pushes those keywords into Redis, mapping each token to its document ID for quick lookups.

### Look ups are ranked with the TF-IDF algorithm

- The index contains the term frequency of its token and is ranked with other variables such as total document count and
  document frequency.
- The frontend provides additonal client-side filtering using the header types.

Adding phrase proximity or BM25 will require a big rewrite to the current ingestion pipeline so its under consideration.

## Performance Benchmarks

The dataset tested contained around 1.21 million CSV rows.

- Total CSV Parse Time: ~2.3 seconds

- MongoDB Ingestion Speed: ~37,000 rows/sec (~32.7s total)

- Keyword Indexing Speed: ~365,000 tokens/sec (~32.7s total)

- Redis Ingestion Speed: ~365,300 commands/sec (~32.7s total, 11.96M SADD commands)

| Metric                   | Throughput         |
|--------------------------|--------------------|
| **Total Pipeline**       | ~33.8k RPS         |
| **Parsing & Processing** | ~37.7k RPS         |
| **Pure Mongo Operation** | ~37.0k RPS         |
| **Indexing**             | ~365.0k tokens/sec |
| **Redis Commands**       | ~365.3k cmds/sec   |
