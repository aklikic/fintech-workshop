// HTTP REST client for Akka SDK ApiGatewayEndpoint
class FinTechHttpClient {
    constructor() {
        // Base URL for our HTTP API Gateway
        this.baseUrl = '/api';

        this.initEventListeners();
    }

    initEventListeners() {
        document.getElementById('createAccountForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.createAccount();
        });

        document.getElementById('createCardForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.createCard();
        });

        document.getElementById('startTransactionForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.startTransaction();
        });


        // Transaction tab event listeners are now handled dynamically in renderTransactionsTable()

        document.getElementById('listTransactionsBtn').addEventListener('click', (e) => {
            e.preventDefault();
            this.listTransactionsByAccount();
        });

        document.getElementById('listAccountsBtn').addEventListener('click', (e) => {
            e.preventDefault();
            this.listAllAccounts();
        });

        // Modal close functionality
        document.getElementById('closeModal').addEventListener('click', () => {
            document.getElementById('transactionModal').style.display = 'none';
        });

        // Close modal when clicking outside of it
        window.addEventListener('click', (event) => {
            const modal = document.getElementById('transactionModal');
            if (event.target === modal) {
                modal.style.display = 'none';
            }
        });
    }

    async createAccount() {
        const resultDiv = document.getElementById('createAccountResult');
        this.showLoading(resultDiv);

        try {
            const accountId = document.getElementById('accountId').value;
            const initialBalance = parseInt(document.getElementById('initialBalance').value);

            const request = {
                accountId: accountId,
                initialBalance: initialBalance
            };

            const response = await this.callHttpApi('POST', '/accounts', request);

            const result = `Account Created:
Account ID: ${response.accountId}
Available Balance: ${response.availableBalance}
Posted Balance: ${response.postedBalance}`;

            this.showSuccess(resultDiv, result);
        } catch (error) {
            this.showError(resultDiv, error);
        }
    }

    async createCard() {
        const resultDiv = document.getElementById('createCardResult');
        this.showLoading(resultDiv);

        try {
            const pan = document.getElementById('cardPan').value;
            const expiryDate = document.getElementById('cardExpiry').value;
            const cvv = document.getElementById('cardCvv').value;
            const accountId = document.getElementById('cardAccountId').value;

            const request = {
                pan: pan,
                expiryDate: expiryDate,
                cvv: cvv,
                accountId: accountId
            };

            const response = await this.callHttpApi('POST', '/cards', request);

            const result = `Card Created:
PAN: ${response.pan}
Expiry Date: ${response.expiryDate}
CVV: ${response.cvv}
Account ID: ${response.accountId}`;

            this.showSuccess(resultDiv, result);
        } catch (error) {
            this.showError(resultDiv, error);
        }
    }

    async startTransaction() {
        const resultDiv = document.getElementById('startTransactionResult');
        this.showLoading(resultDiv);

        try {
            const idempotencyKey = document.getElementById('idempotencyKey').value;
            const transactionId = document.getElementById('transactionId').value;
            const cardPan = document.getElementById('transactionCardPan').value;
            const cardExpiry = document.getElementById('transactionCardExpiry').value;
            const cardCvv = document.getElementById('transactionCardCvv').value;
            const amount = parseInt(document.getElementById('amount').value);
            const currency = document.getElementById('currency').value;

            const request = {
                idempotencyKey: idempotencyKey,
                transactionId: transactionId,
                cardPan: cardPan,
                cardExpiryDate: cardExpiry,
                cardCvv: cardCvv,
                amount: amount,
                currency: currency
            };

            const response = await this.callHttpApi('POST', '/transactions/start', request);

            const result = `Transaction Started:
Result: ${response.result}`;

            this.showSuccess(resultDiv, result);
        } catch (error) {
            this.showError(resultDiv, error);
        }
    }



    async listTransactionsByAccount() {
        const resultDiv = document.getElementById('listTransactionsResult');
        const tableContainer = document.getElementById('transactionsTableContainer');

        this.showLoading(resultDiv);
        tableContainer.style.display = 'none';

        try {
            const accountId = document.getElementById('accountIdForTransactions').value;

            const response = await this.callHttpApi('GET', `/accounts/${accountId}/transactions`);

            if (response.transactions && response.transactions.length > 0) {
                this.showSuccess(resultDiv, `Found ${response.transactions.length} transactions for account: ${accountId}`);
                this.renderTransactionsTable(response.transactions);
                tableContainer.style.display = 'block';
            } else {
                this.showSuccess(resultDiv, `No transactions found for account: ${accountId}`);
                tableContainer.style.display = 'none';
            }
        } catch (error) {
            this.showError(resultDiv, error);
            tableContainer.style.display = 'none';
        }
    }

    renderTransactionsTable(transactions) {
        const tableBody = document.getElementById('transactionsTableBody');
        tableBody.innerHTML = '';

        transactions.forEach(transaction => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${transaction.transactionId}</td>
                <td>${transaction.idempotencyKey}</td>
                <td>${transaction.authResult}</td>
                <td>${transaction.authStatus}</td>
                <td class="action-buttons">
                    <button class="secondary-btn get-details-btn" data-idempotency-key="${transaction.idempotencyKey}">
                        Get Details
                    </button>
                    <button class="primary-btn capture-btn" data-idempotency-key="${transaction.idempotencyKey}">
                        Capture
                    </button>
                </td>
            `;
            tableBody.appendChild(row);
        });

        // Add event listeners to Get Details buttons
        document.querySelectorAll('.get-details-btn').forEach(button => {
            button.addEventListener('click', (e) => {
                const idempotencyKey = e.target.getAttribute('data-idempotency-key');
                this.showTransactionDetails(idempotencyKey);
            });
        });

        // Add event listeners to Capture buttons
        document.querySelectorAll('.capture-btn').forEach(button => {
            button.addEventListener('click', (e) => {
                const idempotencyKey = e.target.getAttribute('data-idempotency-key');
                this.captureTransactionFromTable(idempotencyKey);
            });
        });
    }

    async showTransactionDetails(idempotencyKey) {
        const modal = document.getElementById('transactionModal');
        const detailsDiv = document.getElementById('transactionDetails');

        // Show modal
        modal.style.display = 'block';
        detailsDiv.innerHTML = '<div class="spinner"></div><p>Loading transaction details...</p>';

        try {
            const response = await this.callHttpApi('GET', `/transactions/${idempotencyKey}`);

            const detailsHtml = `
                <div class="transaction-detail-item">
                    <strong>Idempotency Key:</strong> ${response.idempotencyKey}
                </div>
                <div class="transaction-detail-item">
                    <strong>Transaction ID:</strong> ${response.transactionId}
                </div>
                <div class="transaction-detail-item">
                    <strong>Card PAN:</strong> ${response.cardPan}
                </div>
                <div class="transaction-detail-item">
                    <strong>Card Expiry:</strong> ${response.cardExpiryDate}
                </div>
                <div class="transaction-detail-item">
                    <strong>Amount:</strong> ${response.amount} ${response.currency}
                </div>
                <div class="transaction-detail-item">
                    <strong>Auth Code:</strong> ${response.authCode}
                </div>
                <div class="transaction-detail-item">
                    <strong>Auth Result:</strong> ${response.authResult}
                </div>
                <div class="transaction-detail-item">
                    <strong>Auth Status:</strong> ${response.authStatus}
                </div>
                <div class="transaction-detail-item">
                    <strong>Captured:</strong> ${response.captured ? 'Yes' : 'No'}
                </div>
            `;

            detailsDiv.innerHTML = detailsHtml;
        } catch (error) {
            detailsDiv.innerHTML = `<div class="error">Error loading transaction details: ${error.message}</div>`;
        }
    }

    async captureTransactionFromTable(idempotencyKey) {
        try {
            const response = await this.callHttpApi('POST', `/transactions/${idempotencyKey}/capture`);

            // Show success notification
            const resultDiv = document.getElementById('listTransactionsResult');
            const originalContent = resultDiv.textContent;
            this.showSuccess(resultDiv, `Transaction ${idempotencyKey} captured successfully: ${response.result}`);

            // Refresh the table after 2 seconds
            setTimeout(() => {
                resultDiv.textContent = originalContent;
                this.listTransactionsByAccount();
            }, 2000);

        } catch (error) {
            // Show error notification
            const resultDiv = document.getElementById('listTransactionsResult');
            this.showError(resultDiv, `Failed to capture transaction: ${error.message}`);

            // Clear error after 3 seconds
            setTimeout(() => {
                this.listTransactionsByAccount();
            }, 3000);
        }
    }

    async listAllAccounts() {
        const resultDiv = document.getElementById('listAccountsResult');
        const tableContainer = document.getElementById('accountsTableContainer');

        this.showLoading(resultDiv);
        tableContainer.style.display = 'none';

        try {
            const response = await this.callHttpApi('GET', '/accounts');

            if (response.accounts && response.accounts.length > 0) {
                this.showSuccess(resultDiv, `Found ${response.accounts.length} accounts`);
                this.renderAccountsTable(response.accounts);
                tableContainer.style.display = 'block';
            } else {
                this.showSuccess(resultDiv, 'No accounts found');
                tableContainer.style.display = 'none';
            }
        } catch (error) {
            this.showError(resultDiv, error);
            tableContainer.style.display = 'none';
        }
    }

    renderAccountsTable(accounts) {
        const tableBody = document.getElementById('accountsTableBody');
        tableBody.innerHTML = '';

        accounts.forEach(account => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${account.accountId}</td>
                <td>$${account.availableBalance.toLocaleString()}</td>
                <td>$${account.postedBalance.toLocaleString()}</td>
            `;
            tableBody.appendChild(row);
        });
    }

    async callHttpApi(method, endpoint, body = null) {
        const url = this.baseUrl + endpoint;
        const options = {
            method: method,
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            }
        };

        if (body && (method === 'POST' || method === 'PUT' || method === 'PATCH')) {
            options.body = JSON.stringify(body);
        }

        const response = await fetch(url, options);

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`HTTP ${response.status}: ${errorText}`);
        }

        // Handle empty responses
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            return await response.json();
        } else {
            return await response.text();
        }
    }


    showLoading(element) {
        element.className = 'result loading';
        element.innerHTML = '<div class="spinner"></div><p>Making HTTP API call...</p>';
        element.style.display = 'block';
    }

    showSuccess(element, data) {
        element.className = 'result success';
        element.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
        element.style.display = 'block';
    }

    showError(element, error) {
        element.className = 'result error';
        element.textContent = error.message || error.toString();
        element.style.display = 'block';
    }
}

// Tab switching function
function openTab(evt, tabName) {
    var i, tabcontent, tablinks;
    tabcontent = document.getElementsByClassName("tab-content");
    for (i = 0; i < tabcontent.length; i++) {
        tabcontent[i].classList.remove("active");
    }
    tablinks = document.getElementsByClassName("tab-button");
    for (i = 0; i < tablinks.length; i++) {
        tablinks[i].classList.remove("active");
    }
    document.getElementById(tabName).classList.add("active");
    evt.currentTarget.classList.add("active");
}

// Sub-tab switching function
function openSubTab(evt, subTabName) {
    var i, subtabcontent, subtablinks;
    // Get sub-tab content within the same parent tab
    var parentTab = evt.currentTarget.closest('.tab-content');
    subtabcontent = parentTab.getElementsByClassName("sub-tab-content");
    for (i = 0; i < subtabcontent.length; i++) {
        subtabcontent[i].classList.remove("active");
    }
    subtablinks = parentTab.getElementsByClassName("sub-tab-button");
    for (i = 0; i < subtablinks.length; i++) {
        subtablinks[i].classList.remove("active");
    }
    document.getElementById(subTabName).classList.add("active");
    evt.currentTarget.classList.add("active");
}

// Initialize the HTTP client when the page loads
document.addEventListener('DOMContentLoaded', () => {
    window.finTechHttpClient = new FinTechHttpClient();
});