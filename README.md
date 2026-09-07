# Excel Barcode and QR Code Reconciler

A full-stack application that compares barcode and QR-code images with an Excel spreadsheet or table image, highlights matching rows, and produces a downloadable Excel workbook.

The application uses:

- React and Vite for the frontend
- Spring Boot for the REST API and background worker
- CloudAMQP RabbitMQ for asynchronous job delivery
- ZXing for local barcode and QR-code decoding
- Gemini for table extraction and barcode fallback
- Apache POI for Excel workbook processing and highlighting

## Architecture overview

~~~mermaid
flowchart LR
    frontend[Frontend Web App]
    api[Spring Boot API]
    exchange[CloudAMQP Exchange]
    queue[barcode.reconciliation.queue]
    listener[Spring Boot RabbitMQ Listener]
    pipeline[ReconciliationService]
    storage[Runtime File Storage]
    gemini[Gemini API]

    frontend -->|Upload, status, result, and download| api
    api -->|Save uploaded files| storage
    api -.->|Publish JSON job| exchange
    exchange -.->|Route by routing key| queue
    queue -.->|Deliver job| listener
    listener -->|Load files and call service| pipeline
    pipeline -->|Read and write files| storage
    pipeline -.->|Table extraction and barcode fallback| gemini
~~~

RabbitMQ transports and waits with the JSON job. It does not read Excel files, decode images, call Gemini, or create the final workbook. Those operations run inside Spring Boot.

## End-to-end processing flow

~~~text
Frontend
  │
  │ POST /api/v1/barcodes/reconcile
  │
  │ Multipart request:
  │ ├─ Excel/table file
  │ ├─ Barcode images
  │ ├─ columnName
  │ └─ highlightFullRow
  ▼
BarcodeReconciliationController
  │
  ├─ Validates the uploaded files
  ├─ Creates a unique reconciliationId
  ├─ Creates the initial QUEUED status
  ├─ Saves files to:
  │  runtime/reconciliations/<reconciliationId>/
  │  ├─ excel/
  │  └─ images/
  │
  ├─ Creates BarcodeReconciliationRequest:
  │  ├─ reconciliationId
  │  ├─ excelFilePath
  │  ├─ imageFilePaths
  │  ├─ columnName
  │  └─ highlightFullRow
  │
  └─ Returns HTTP 202 Accepted with the job ID
     and status URL
  ▼
BarcodeReconciliationPublisher
  │
  └─ Converts the request object to JSON
     and publishes it through RabbitTemplate
  ▼
CloudAMQP RabbitMQ
  │
  ├─ Exchange:
  │  barcode.reconciliation.exchange
  │
  ├─ Routing key:
  │  barcode.reconciliation.requested
  │
  └─ Queue:
     barcode.reconciliation.queue
     │
     └─ Stores the JSON job until a listener receives it
        ▼
BarcodeReconciliationListener
  │
  ├─ Receives BarcodeReconciliationRequest
  ├─ Changes status to PROCESSING
  ├─ Loads the Excel file from excelFilePath
  ├─ Loads all images from imageFilePaths
  └─ Calls ReconciliationService
     ▼
ReconciliationService
  │
  ├─ ExcelImageExtractorService
  │  └─ Extracts and validates the Excel/table data
  │
  ├─ BarcodeDecoderService
  │  ├─ Sends images to ZXing
  │  ├─ Decodes images in parallel
  │  └─ Sends failed scans to Gemini fallback
  │
  ├─ ExcelHighlightService
  │  ├─ Matches decoded barcodes with Excel rows
  │  └─ Highlights matching rows
  │
  └─ Builds ReconciliationResponse
     ├─ Barcode scan results
     ├─ Matched and unmatched codes
     ├─ Preview rows
     ├─ Highlighted workbook data
     └─ Processing statistics
     ▼
BarcodeReconciliationListener
  │
  ├─ Saves the final workbook as:
  │  runtime/reconciliations/<reconciliationId>/result.xlsx
  ├─ Stores the ReconciliationResponse
  ├─ Changes status to COMPLETED
  └─ Returns successfully from the listener method
     ▼
RabbitMQ acknowledgement
  │
  └─ RabbitMQ removes the successfully processed message
     from barcode.reconciliation.queue
     ▼
Frontend status polling
  │
  │ GET /api/v1/barcode-reconciliations/<reconciliationId>
  │
  └─ Receives status information:
     ├─ status: COMPLETED
     ├─ stage: Completed
     ├─ errorMessage: null
     └─ resultAvailable: true
     ▼
Frontend result retrieval
  │
  ├─ GET /api/v1/barcode-reconciliations/<reconciliationId>/result
  │  └─ Retrieves the ReconciliationResponse
  │
  └─ GET /api/v1/barcode-reconciliations/<reconciliationId>/download
     └─ Downloads the highlighted Excel file
~~~

## RabbitMQ job message

The queue stores a JSON message containing references to the uploaded files and the options for the reconciliation job.

~~~json
{
  "reconciliationId": "generated-job-id",
  "excelFilePath": "runtime/reconciliations/<id>/excel/input.xlsx",
  "imageFilePaths": [
    "runtime/reconciliations/<id>/images/1.jpg",
    "runtime/reconciliations/<id>/images/2.jpg"
  ],
  "columnName": "QR Barcode",
  "highlightFullRow": false
}
~~~

RabbitMQ stores the message, not the actual Excel or image files. The listener loads those files from Spring Boot runtime storage.

## RabbitMQ reliability

- The main queue is durable.
- The listener receives one reconciliation job at a time.
- A successful listener completion acknowledges and removes the message.
- Failed processing is retried up to three attempts.
- Repeatedly failed messages are routed to barcode.reconciliation.failed.

## API endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | /api/v1/barcodes/reconcile | Upload files and create a queued job |
| GET | /api/v1/barcode-reconciliations/<reconciliationId> | Read job status |
| GET | /api/v1/barcode-reconciliations/<reconciliationId>/result | Read the completed JSON result |
| GET | /api/v1/barcode-reconciliations/<reconciliationId>/download | Download the highlighted workbook |
| GET | /api/v1/health | Check that the Spring Boot API is reachable |

The status and result endpoints read data from Spring Boot after processing. They do not read the completed response from RabbitMQ.

## Environment configuration

Do not commit real credentials. Configure these values locally or in the Render backend service.

### Local RabbitMQ

~~~env
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_VIRTUAL_HOST=/
RABBITMQ_SSL_ENABLED=false
~~~

### CloudAMQP production

~~~env
RABBITMQ_HOST=<CloudAMQP hostname>
RABBITMQ_PORT=5671
RABBITMQ_USERNAME=<CloudAMQP username>
RABBITMQ_PASSWORD=<CloudAMQP password>
RABBITMQ_VIRTUAL_HOST=<CloudAMQP virtual host>
RABBITMQ_SSL_ENABLED=true
~~~

Gemini configuration:

~~~env
GEMINI_API_KEY=<Gemini API key>
GEMINI_API_MODEL=gemini-flash-lite-latest
GEMINI_API_URL=https://generativelanguage.googleapis.com/v1beta/models
~~~

Other application settings:

~~~env
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
RECONCILIATION_STORAGE_ROOT=runtime/reconciliations
~~~

The env files are ignored by Git. Use Render's environment-variable settings for production secrets.

## Local development

### Prerequisites

- Java 17 or newer
- Maven 3.8 or newer
- Node.js 18 or newer
- A running RabbitMQ instance, either local or hosted
- A Gemini API key

### Start the backend

~~~bash
cd backend
mvn spring-boot:run
~~~

The backend runs on http://localhost:8080 by default.

### Start the frontend

~~~bash
cd frontend
npm install
npm run dev
~~~

The frontend runs on http://localhost:5173 by default.

### Run a reconciliation

1. Upload an Excel file or Excel-table image in Step 1.
2. Upload barcode or QR-code images in Step 2.
3. Select the barcode column and highlighting option.
4. Start the reconciliation.
5. Wait for the status to change from QUEUED to PROCESSING to COMPLETED.
6. Review the preview and download the highlighted workbook.

## Deployment flow

1. Create or start the CloudAMQP RabbitMQ instance.
2. Configure the production RabbitMQ and Gemini variables in the Render backend service.
3. Deploy the Spring Boot backend from backend/Dockerfile.
4. Confirm the backend health endpoint returns status: UP.
5. Submit one real reconciliation and confirm it reaches COMPLETED.
6. Configure the frontend API URL to point to the live backend.
7. Deploy the frontend and test upload, polling, preview, and download.

RabbitMQ is hosted separately from the Spring Boot service. The backend connects to it through the CloudAMQP hostname and TLS port.

## Project structure

~~~text
.
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/excel/reconciler/
│       │   ├── config/
│       │   │   └── RabbitMqConfig.java
│       │   ├── controller/
│       │   │   └── BarcodeReconciliationController.java
│       │   ├── model/
│       │   │   └── BarcodeReconciliationRequest.java
│       │   └── service/
│       │       ├── BarcodeDecoderService.java
│       │       ├── BarcodeReconciliationListener.java
│       │       ├── BarcodeReconciliationPublisher.java
│       │       ├── ExcelHighlightService.java
│       │       ├── ExcelImageExtractorService.java
│       │       ├── LocalReconciliationFileStorageService.java
│       │       └── ReconciliationService.java
│       └── resources/application.yml
├── frontend/
│   ├── package.json
│   └── src/
│       ├── app/
│       └── features/reconciliation/
└── README.md
~~~

## Verification commands

Backend compile:

~~~bash
cd backend
mvn -q -DskipTests compile
~~~

Backend tests:

~~~bash
cd backend
mvn test
~~~

Frontend build and tests:

~~~bash
cd frontend
npm run build
npm test
~~~

## Production storage note

The current implementation stores uploaded files and results in local runtime storage and keeps job status in the running backend process. RabbitMQ can retain a queued message, but it cannot restore a file that was lost during a backend restart or redeploy.

For restart-safe production storage, move uploaded files and results to object storage and persist reconciliation status in a database.
