# Cloud Text Analysis 

### Authors

- Gal Schwartz – 322271891  
- Dina Gurevich – 322405911

---

## 1. Overview

This project implements a **distributed text‑analysis system** on **AWS**.

The system is composed of three components:

1. Local application- runs on the user's machine.
2. Manager Node- EC2 instance that coordinates Workers.
3. Worker Nodes- EC2 instances created dynamically to process analysis tasks.

The system receives as input a local file containing lines of:

```
<ANALYSIS_TYPE>\t<URL>
```

where `ANALYSIS_TYPE ∈ {POS, CONSTITUENCY, DEPENDENCY}` and the URL points to a text file online.

System workflow:

1. Local application uploads the input file to S3.
2. Manager distributes each (analysis, URL) as tasks to Worker EC2 instances.
3. Each Worker downloads and analyzes the text, then uploads the result to S3.
4. Manager aggregates all results into a single HTML summary.
5. Local application downloads the HTML summary to the requested output path.

All communication between components uses **S3** (storage) and **SQS** (messaging).

---

## 2. AWS Resources Used

### 2.1 S3 Buckets

#### **Application bucket: `cloud-text-analysis`**

* `inputs/` – input files uploaded by Local Applications.
* `outputs/<taskId>/` – worker result files.
* `summaries/` – HTML summary files.

#### **Artifacts bucket: `cloud-text-artifacts`**

* Stores `manager.jar` and `worker.jar` for EC2 startup scripts.

### 2.2 SQS Queues (4 queues)

#### **1. Manager Request Queue** (Local → Manager)

Shared by all Local Applications.

Message type: `TaskRequest`

#### **2. Worker Task Queue** (Manager → Workers)

* Used by the Manager to distribute subtasks
* Every Worker pulls messages independently
* Enables parallel processing of URLs
  
Message type: `WorkerTaskMessage`

#### **3. Worker Result Queue** (Workers → Manager)

Message type: `WorkerResultMessage`

#### **4. Local Response Queue** (Manager → specific Local)

* Created dynamically per LocalApplication run.
* The Manager sends SummaryResponse only to this queue, ensuring:
 * No cross-interference between different local clients
 * No mixing responses of multiple tasks
* System remains scalable even with thousands of clients

Message type: `SummaryResponse`

---

## 3. Components Responsibilities

### 3.1 Local Application

Responsibilities:

* Checks if a Manager EC2 instance exists. If not - launches it.
* Create a dedicated response queue.
* Upload input file to S3.
* Send new task (`TaskRequest`) to Manager Request Queue.
* Wait for `SummaryResponse` and download the HTML summary.
* On exit: delete the personal queue and close AWS clients.

If `terminate` is passed:

* Manager will terminate all workers and then itself.

### 3.2 Manager

Key actions:

* Downloads input file from S3.
* Creates a separate SQS message per <url, analysis-type> (`TaskState`).
* Counts number of messages → determines number of workers required (ceil(total / n)).
* Launches EC2 Worker nodes if needed.
* Send WorkerTaskMessages.
* Collect results and build summary HTML.
* Uploads summary to S3.
* Send SummaryResponse back to Local App.
* Handle system termination.

### 3.3 Worker

Loop:

* Poll Worker Task Queue.
* Download file from URL.
* Run Stanford parser.
* Upload analyzed result to S3.
* Send WorkerResultMessage.

---

## 4. Scalability

* Workers scale dynamically based on number of messages.
Concretely, the Manager computes:
`requiredWorkers = ceil(totalMessages / n)`
where `totalMessages` is the number of worker tasks for a given input file, and `n` is the maximum number of URLs we allow each Worker to process. It then compares this to the current number of active Worker instances (tagged `Role=Worker`) and launches additional workers if needed (within the AWS 19-instance limit). This gives a clear and controllable scaling policy.
* All workers consume from one shared queue.
* Thousands or millions of tasks are conceptually supported.
* No single point of blocking — no thread waits unnecessarily.
* Multiple LocalApplications can run simultaneously.
* Manager handles multiple user requests in parallel.
* Thread-safe `ConcurrentMap` for TaskState.
* Each Worker instance is intentionally single-threaded. Instead of using many threads inside one Worker, we scale horizontally by launching more Worker EC2 instances when the Worker Task Queue is large. This follows the “scale out, not up” cloud principle and avoids complex synchronization around the NLP parser.
* On the Manager side, scalability is achieved by separating responsibilities into dedicated threads (local requests poller, worker results poller) and a thread pool that can process multiple TaskRequests in parallel. This allows the Manager to coordinate many Locals and Workers without becoming a bottleneck.
* Because all coordination is done via SQS queues (asynchronous messaging), Locals, Manager, and Workers remain loosely coupled, which is what enables the system to scale to many clients and many workers independently.

---

## 5. Persistence & Fault Tolerance

### 5.1 S3 + SQS Durability

* All data stored in S3 is highly durable.
* SQS ensures messages persist until explicitly deleted.

### 5.2 Worker Failures

* Visibility timeout ensures stalled tasks reappear.
* Worker exceptions return error messages instead of crashing.
* If the text file is not available or an exception occurs while parsing, the Worker catches the exception and sends a `WorkerResultMessage` with `outputS3Key = null` and `error` containing a short description. The Manager still counts this item as “done” and includes the error text in the HTML summary for that URL and analysis type, as required in the assignment.


### 5.3 Manager Failures

* Queues retain all tasks and results.
* New Manager instance can resume work.

---

## 6. Threads & Concurrency

The system uses multithreading primarily inside the Manager node.  
This allows the Manager to coordinate many LocalApplications and Workers concurrently without becoming a bottleneck.

### Manager Threads

The Manager runs **three main concurrent components**:

* LocalRequestsPoller thread - This thread continuously long-polls the *Manager Request Queue* for `TaskRequest` messages sent from LocalApplications.
* WorkerResultsPoller thread - This thread continuously long-polls the *Worker Result Queue* for `WorkerResultMessage`s.
* ExecutorService thread pool - Used for concurrent execution of `handleTaskRequest` operations.
Many `TaskRequest`s can be processed simultaneously.

---

### Thread Safety Guarantees

Shared state inside the Manager is handled using **thread-safe structures**:

* `ConcurrentMap<String, TaskState>` for all active tasks.
* Synchronization inside `TaskState.addResult()` ensures safe concurrent updates.
* AWS SDK calls are thread-safe and non-blocking.

---

### Worker Node Concurrency

Worker nodes intentionally run in a **single-threaded loop**:

* Stanford NLP parsing is CPU-heavy and not thread-safe by default.
* The system uses **horizontal scaling** instead of vertical scaling:  
  the Manager launches additional Worker instances rather than adding threads.

This keeps the Worker implementation simple and avoids race conditions, while still achieving high parallelism across many Workers.

---

### LocalApplication Concurrency

The LocalApplication is single-threaded:

* It sends a single task.
* It blocks waiting for the summary on its dedicated response queue.
* No parallelism is needed or beneficial.

---

### Summary

The thread architecture ensures:

* High throughput in the Manager.
* Correctness and safety of shared data structures.
* Full scalability through parallel Workers instead of complicated multi-threaded Workers.
* A clean separation of responsibilities between polling, processing, and coordination threads.


## 7. Termination Management

Triggered by LocalApplication passing `terminate`.

Manager:

* Accepts no new tasks.
* Finishes ongoing tasks.
* Sends all summaries.
* Terminates all Workers.
* Terminates itself.

LocalApplication:

* Deletes response queue.
* Exits cleanly.

---

## 8. Security Considerations

* No AWS credentials in source code.
* Uses IAM roles and DefaultCredentialsProvider.
* Public-read ACL only for required output files.
* Artifacts stored in a controlled S3 bucket.

---

## 9. How to Build and Run

### 9.1 Build

```
mvn clean package
```

Produces:

* `localapp.jar`
* `manager.jar`
* `worker.jar`

* All jars are fat jars
* bootstraping `worker.jar` `manager.jar` via UserData scripts.


### 9.2 AWS Setup

* Create S3 buckets.
* Create SQS queues.
* Create IAM roles.
* Create Security Group ID and Key Pair.
* Create AMI (same for Manager and Worker) → store its ID in LocalAppConfig.MANAGER_AMI_ID and ManagerConfig.WORKER_AMI_ID
  
Installed on AMI:

    Java SDK
    
    AWS CLI
    
    Ubunto
    
Manager instance t2.micro

Worker instance t3.medium

* Configure user‑data scripts and AMIs.
* Upload artifacts.

### 9.3 Run

```
java -jar localapp.jar <inputFile> <outputFile> <n> [terminate]
```


## 10. Distributed System Properties

* All communication is asynchronous via SQS.
* Components run independently.
* Supports large-scale concurrency and many clients.
* Follows distributed design principles.

Runtime on sample input: 26 minutes
n used in sample run: 2
