# GoodLink - NGO Volunteer Platform Backend

GoodLink is a Java 17 and Spring Boot backend for an NGO volunteer coordination platform. Input and volunteer interactions are managed through a **Telegram Bot** (using the `telegrambots` library with long polling, no webhooks/public IP required), while notifications, confirmations, and completion certificates are dispatched via **Email** (Spring Mail via Gmail SMTP).

To guarantee algorithmic rigour and clean system design, all core collection and routing logic is backed by **hand-written custom data structures** located in package `com.goodlink.ds` without using standard `java.util` collection frameworks for core operations.

---

## 📊 Feature to Data Structure Mapping Table

| Feature / Domain Requirement | Custom Data Structure | Package | Key Operations | Rationale & Behavioral Specification |
| :--- | :--- | :--- | :--- | :--- |
| **NGO Directory & In-Order Listing** | `NgoBST` (Binary Search Tree) | `com.goodlink.ds` | `insert(NGO)`, `search(id)`, `inOrder(Consumer)`, `inOrderPrint()` | Organizes NGOs keyed by integer ID in average $O(\log n)$ search time. In-order traversal outputs organizations in strictly sorted ID order for the `NGOS` bot command. |
| **Volunteers per Activity** | `MyLinkedList<Volunteer>` | `com.goodlink.ds` | `add(T)`, `remove(T)`, `contains(T)`, `forEach(Consumer)`, `size()` | Dynamic singly-linked list embedded in each `Activity`. Stores registered volunteers without fixed memory boundaries or standard collection overhead. |
| **Volunteer Profiles** | `MyLinkedList<Volunteer>` | `com.goodlink.ds` | `add(T)`, `contains(T)`, `forEach(Consumer)`, `size()` | Maps Telegram `chatId` to volunteer profiles (`name`, `email`) using clean node traversal. |
| **Achievements Wall** | `MyLinkedList<Achievement>` | `com.goodlink.ds` | `addFirst(T)`, `forEach(Consumer)`, `size()` | Uses prepending (`addFirst`) so newly completed activities and platform milestones are rendered **newest first** when queried with `ACHIEVEMENTS`. |
| **Donations Queue** | `MyQueue<Donation>` | `com.goodlink.ds` | `enqueue(T)`, `dequeue()`, `peek()`, `size()` | True FIFO (First-In, First-Out) queue ensuring fair, sequential processing of incoming pledged goods (e.g. blankets, food kits). |
| **Emergency Requests Dispatch** | `UrgentRequests` (Dual `MyQueue`) | `com.goodlink.ds` | `enqueue(T)`, `dequeue()`, `peek()`, `size()`, `getUrgentCount()` | Multi-queue structure with an `urgentQueue` and `normalQueue`. The strict operational invariant guarantees **urgent requests are ALWAYS served before normal requests**. |
| **NGO Classification Hierarchy** | `CategoryTree` (General N-ary Tree) | `com.goodlink.ds` | `addCategory(parent, child)`, `find(name)`, `printTree()` | Models hierarchical NGO taxonomy (e.g., `Elder Care -> Old Age Homes / Dementia Care`, `Child Welfare -> Orphanages`). Provides ASCII tree visualization. |
| **Scheduled Activities Catalog** | `ActivityArray` (Dynamic Array) | `com.goodlink.ds` | `add(Activity)`, `findById(id)`, `get(index)`, `forEach(Consumer)`, `size()` | Primitive `Activity[]` dynamic array with automatic geometric capacity doubling. Guarantees fast direct indexing. |

---

## 🛠️ Architecture & Tech Stack

- **Java Version**: OpenJDK 17 LTS
- **Framework**: Spring Boot 3.2.5 (Spring Web, Spring Mail)
- **Telegram Bot**: `telegrambots:6.9.7.1` (Long polling mode, no public IP or SSL certificate required)
- **PDF Generation**: OpenPDF `1.3.40` (`com.github.librepdf:openpdf`) for high-fidelity certificate issuance
- **Data Structures**: Hand-crafted in package `com.goodlink.ds`
- **Build Tool**: Apache Maven 3.9+ with included `mvnw.cmd` wrapper

---

## 🚀 Setup & Execution Guide

### 1. Prerequisites
- **Java 17 JDK** installed and available on PATH (`java -version`).
- **Telegram Bot Token** from [@BotFather](https://t.me/BotFather).
- **Gmail Account & App Password** (for sending emails via Gmail SMTP).

### 2. Configure Environment Variables
Set the following environment variables in your terminal or system:

#### Windows (PowerShell)
```powershell
$env:TELEGRAM_BOT_TOKEN="your_telegram_bot_token_here"
$env:MAIL_USER="your_email@gmail.com"
$env:MAIL_PASS="your_16_character_app_password"
# Optional: redirect all NGO-addressed emails during testing
$env:NGO_EMAIL_OVERRIDE="test-inbox@yourdomain.com"
```

#### Linux / macOS (Bash / Zsh)
```bash
export TELEGRAM_BOT_TOKEN="your_telegram_bot_token_here"
export MAIL_USER="your_email@gmail.com"
export MAIL_PASS="your_16_character_app_password"
# Optional: redirect all NGO-addressed emails during testing
export NGO_EMAIL_OVERRIDE="test-inbox@yourdomain.com"
```

> **Note on NGO Email Override & Testing:** If `NGO_EMAIL_OVERRIDE` is set, all emails intended for an NGO are redirected to that single address, and the subject is prefixed with `[TEST - intended for <ngo email>]`. If not set, emails go directly to each NGO's real contact email. Volunteer and donor emails (`REGISTER`, `JOIN`, `DONATE`, completion certificates) are **never affected** and always go to the volunteer's registered email address.
>
> **Note on Email & Telegram safety:** If `MAIL_USER` and `MAIL_PASS` are omitted, GoodLink automatically runs in **simulation mode**, cleanly logging all outgoing emails and PDF attachments without crashing. Similarly, if `TELEGRAM_BOT_TOKEN` is unset, the bot logs an informative notice while the Spring Boot REST API and platform services remain fully operational.

### 3. Build the Project
Run tests and build the fat JAR:
```powershell
.\mvnw.cmd clean package
```
*(On Linux/Mac: `./mvnw clean package` or `mvn clean package`)*

### 4. Run the Application
Start the Spring Boot backend server on port `8081` (configurable via `SERVER_PORT`):
```powershell
.\mvnw.cmd spring-boot:run
```
Or execute the packaged executable JAR directly:
```powershell
java -jar target/goodlink-1.0.0.jar
```

### 5. Access Interactive Web Portal & Algorithmic Lab
Once started, open your browser at:
👉 **[http://localhost:8081/](http://localhost:8081/)**

The Web Application features:
- **Data Structures Lab**: Real-time visualizers for all 6 custom algorithmic structures (`NgoBST`, `ActivityArray`, `UrgentRequests` dual queues, `MyQueue` FIFO donations, `MyLinkedList` newest-first achievements, and `CategoryTree` ASCII hierarchy).
- **Interactive Bot Terminal**: Full simulated Telegram chat interface allowing you to test bot commands (`/start`, `NGOS`, `JOIN`, `DONATE`, `URGENT`, `ACHIEVEMENTS`, `CATEGORIES`) directly in the browser even without a bot token.
- **Volunteer Management**: Live volunteer registration, activity rosters, and one-click completion.
- **OpenPDF Certificate Viewer**: Preview and download generated certificates directly from the browser.

---

## 🤖 Telegram Bot Commands

| Command | Syntax | Action & Behavior |
| :--- | :--- | :--- |
| **Welcome / Help** | `/start` or `HELP` | Returns welcome banner and list of available commands. |
| **Register Profile** | `REGISTER <name> <email>` | Saves user's Telegram `chatId` $\rightarrow$ `Volunteer(name, email)`. |
| **Register NGO Email** | `NGOREGISTER <ngoId> <contactEmail>` | Sets/updates an NGO's real contact email at runtime in `NgoBST`. |
| **List NGOs** | `NGOS` | Displays all registered NGOs in sorted ID order via in-order BST traversal. |
| **List Activities** | `ACTIVITIES` | Lists upcoming volunteering activities from `ActivityArray`. |
| **Join Activity** | `JOIN <activityId>` | Enrolls volunteer into the activity and dispatches a registration confirmation **email**. |
| **Pledge Donation** | `DONATE <item> <quantity>` | Enqueues donation into FIFO queue and sends confirmation email. |
| **Urgent Request** | `URGENT <BLOOD\|FOOD> <details> <city>` | Inserts critical need into urgent queue and dispatches priority alert email to NGO admin. |
| **Achievements Wall** | `ACHIEVEMENTS` | Shows recent achievements and certificates in newest-first order. |
| **Category Tree** | `CATEGORIES` | Visualizes the NGO classification hierarchy. |
| **Unknown Text** | *(Any unknown message)* | Automatically replies in chat with the help menu. |

*Always replies in the Telegram chat in addition to dispatching confirmation emails.*

---

## 🎓 Admin REST API & Certificates

### Mark Participation Complete & Issue Certificate
Certificates are generated and emailed **only after completion**, never at registration.

- **Endpoint**: `POST /admin/complete/{activityId}/{chatId}`
- **Curl Example**:
  ```bash
  curl -X POST http://localhost:8081/admin/complete/1/987654321
  ```
- **Response**:
  ```json
  {
    "status": "SUCCESS",
    "activityId": 1,
    "chatId": 987654321,
    "message": "SUCCESS: Marked participation complete for Bruce Wayne (ChatId: 987654321) on Activity #1 (Weekend Companion Care at Silver Oak). Certificate PDF generated and emailed to bruce@wayne-enterprises.com. Achievement posted to wall."
  }
  ```

### Platform Health & Data Structures Dashboard
- **Endpoint**: `GET /admin/status`
- **Curl Example**:
  ```bash
  curl http://localhost:8081/admin/status
  ```
- **Response**:
  ```json
  {
    "platform": "GoodLink NGO Volunteer Platform",
    "ngosInBST": 8,
    "activitiesInArray": 8,
    "donationsInQueue": 1,
    "urgentRequestsQueue": {
      "total": 1,
      "urgent": 1,
      "normal": 0
    },
    "achievementsOnWall": 3
  }
  ```

---

## 🧪 Testing

Execute the comprehensive test suite verifying all 6 custom data structures, PDF generation, bot commands, and admin APIs:
```powershell
.\mvnw.cmd test
```
All 9 test suites pass with zero failures:
1. `testMyLinkedList`
2. `testMyQueue`
3. `testNgoBST`
4. `testUrgentRequests`
5. `testCategoryTree`
6. `testActivityArray`
7. `testPdfCertificateGeneration`
8. `testBotCommands`
9. `testCompleteParticipation`
