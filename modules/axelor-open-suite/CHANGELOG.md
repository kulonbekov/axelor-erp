## [7.0.1] (2023-05-11)

#### Fixed

* Update axelor-studio to version 1.0.1 with multiples fixes made to the app builder.
* Invoice: fixed bank details being required for wrong payment modes.
* Invoice: fixed an issue blocking advance payment invoice creation when the lines were missing an account.
* Job application: fixed an error occuring when creating a job application without setting a manager.
* Bank reconciliation: added missing translation for "Bank reconciliation lines" in french.
* Product: fixed an issue preventing product copy when using sequence by product category.
* Bank reconciliation/Bank statement rule: added a control in auto accounting process to check if bank detail bank account and bank statement rule cash account are the same.
* Tracking number search: fixed an error occurring when using the tracking number search.
* Stock move: fixed an issue when creating tracking number from an unsaved stock move. If we do not save the stock move, tracking number are now correctly deleted.
* Sale order: fixed an issue where sale order templates were displayed from the 'Historical' menu entry.
* Bank reconciliation: fixed issue preventing to select move lines to reconcile them. 
* Accounting payment vat report: fixed wrong french translations.
* MRP: fixed an JNPE error when deleting a purchase order generated by a MRP.
* Partner: added missing translation on partner size selection.
* Public holiday events planning: set the holidays calendar in a dynamic way to avoid it become outdated in the demo data.
* VAT amount received accounting report: fixed height limit and 40 page interval break limit.
* Invoice payment: fixed payment with different currencies.
* Accounting report das 2: fixed currency required in process.
* Payment Voucher: fixed excess on payment amount, generate an unreconciled move line with the difference.
* Bank reconciliation: fixed tax computation with auto accounting.
* Sale, Stock, CRM, Supplychain printings: fixed an issue were pictures with specific filenames were not displayed in the printings.
* Accounting batch: added missing filter on year.
* Move line: fixed analytic account domain when no analytic rules are based on this account.
* Purchase order: stock location is not required anymore if there are no purchase order lines with stock managed product.
* Custom accounting reports: fixed accounting reports "Display Details" feature.
* Accounting situation: display VAT system select when the partner is internal.
* Invoice: fixed wrong alert message when invoiced quantity was superior to delivered or ordered qty.
* Project report: fixed error preventing the generation of the PDF report for projects.
* Project: Display "Ticket" instead of "Project Task" in Activities tab when the activity is from a ticket.
* Opportunity: added missing sequence on the Kanban and Card view.
* Payment session: select/unselect buttons are now hidden when status is not in progress.
* Analytic move line query: fixed filter on analytic account.
* Move: fixed default currency selection, now the currency from the partner is automatically selected, and if the partner is missing the currency from the company is used.
* Bank reconciliation: fixed initial and final balance when importing multiple statements.
* Accounting report: fixed translation of currency title.
* Bank order: fixed an error preventing the validation of a bank order.
* Inventory: fixed UI issue by preventing unit from being changed in inventory lines.
* Stock rules: now correctly apply stock rules when minimum quantity is zero.

#### Removed

* Because of a refactor, action-record-initialize-permission-validation-move is not used anymore and is now deleted.

* Delete action 'action-record-analytic-distribution-type-select' since its not used anymore.

You can run following SQL script to update your database:

```sql
DELETE FROM meta_action WHERE name='action-record-initialize-permission-validation-move';

DELETE FROM meta_action WHERE name='action-record-analytic-distribution-type-select';
```


## [7.0.0] (2023-04-28)


#### Upgrade to AOP 6.1

* See [AOP migration guides](https://docs.axelor.com/adk/6.1/migrations.html) for AOP migration details
* Upgraded most libraries dependencies.
* Group: new option to enable collaboration
* Studio, BPM, Message and Tools leave Axelor Open Suite to become AOP addons
    * axelor-tools became axelor-utils
* Studio and BPM upgraded
    * Merge Studio and BPM module to a single module and create two different apps
* Apps logic is integrated into the studio
    * apps definition using YAML
    * auto-installer moved from Base to Studio
    * Add new types for apps (Standard, Addons, Enterprise, Custom and Others)
* Web app: application.properties renamed to axelor-config.properties and completed.
    * See [AOP documentation](https://docs.axelor.com/adk/latest/migrations/migration-6.0.html#configurations-naming) for parameter changes related to AOP.
    *  See details for parameter changes related to AOS
        <details>
        `aos.api.enable` is renamed `utils.api.enable` and is now true by default.
        `aos.apps.install-apps` is renamed `studio.apps.install`
        `axelor.report.use.embedded.engine` is renamed `reports.aos.use-embedded-engine`
        `axelor.report.engine` is renamed `reports.aos.external-engine`
        `axelor.report.resource.path` is renamed `reports.aos.resource-path`
        </details>

#### Features

* Swagger
    * API: implement OpenAPI with Swagger UI.
        <details>
            Complete the properties `aos.swagger.enable` and `aos.swagger.resource-packages` in the axelor-config.properties to enable the API documentation menu in Technical maintenance.
        </details>
* Mobile settings
New module to configure the new [Axelor Open Mobile](https://github.com/axelor/axelor-mobile)
* TracebackService: automatically use tracebackservice on controller exceptions
Now, for every controller methods in AOS packages ending with `web`, any
exception will create a traceback.

#### Changes

* Supplychain module: remove bank-payment dependency
* AxelorMessageException: Moved from Message module to Base
* Add order to all menus
    * Add a gap of 100 between menus
    * Negative value for root menus and positive for others
* Stock: reworked all menus in stock module menus
* Account: rework accounting move form view to optimize responsiveness.
* CRM: App configurations are not required anymore.
closedWinOpportunityStatus, closedLostOpportunityStatus, salesPropositionStatus can now be left empty in the configuration. If the related features are used, a message will be shown to inform the user that the configuration must be made.
* Change several dates to dateTime
    * axelor-base: Period
    * axelor-purchase: PurchaseOrder
    * axelor-account: Invoice, InvoiceTerm, Reconcile, ReconcileGroup, ClosureAssistantLine
    * axelor-bank-payment: BankReconciliation
    * axelor-stock: Inventory
    * axelor-production: UnitCostCalculation
    * axelor-human-resources: Timesheet, PayrollPreparation, LeaveRequest, Expense, LunchVoucherMgt
    * axelor-contract: ContractVersion
* Date format:
    * Add a new locale per company
    * Use company locale and/or user locale instead of hard coded date format
* Business project: exculdeTaskInvoicing renamed to excludeTaskInvoicing.
* Template: Add 'help' tab for mail templates.
* New french admin-fr user in demo data
* Add tracking in different forms (app configurations, ebics, etc...)

#### Removed

* Removed deprecated IException interfaces (replaced by new ExceptionMessage java classes)
* Removed all translations present in source code except english and french.
* Removed axelor-project-dms module.
* Removed axelor-mobile module (replaced by axelor-mobile-settings).
* Removed Querie model.
* SaleOrder: removed following unused fields:
    * `invoicedFirstDate`
    * `nextInvPeriodStartDate`
* PaymentSession: removed cancellationDate field.
* Account: removed unused configuration for ventilated invoices cancelation.

#### Fixed

* Password : passwords fields are now encrypted
    <details>
        Concerned models : Ebics User, Calendar and Partner.
        You can now encrypt old fields by using this task :
        `gradlew database --encrypt`
    </details>


[7.0.1]: https://github.com/axelor/axelor-open-suite/compare/v7.0.0...v7.0.1
[7.0.0]: https://github.com/axelor/axelor-open-suite/compare/v6.5.7...v7.0.0