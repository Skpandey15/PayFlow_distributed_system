// Application user for the Fraud context: readWrite on its own database only (least privilege).
// Runs once, on first container start, as the root user created from MONGO_INITDB_ROOT_*.
const fraud = db.getSiblingDB('payflow_fraud');
fraud.createUser({
  user: 'payflow_fraud_app',
  pwd: process.env.PAYFLOW_MONGO_PASSWORD,
  roles: [{ role: 'readWrite', db: 'payflow_fraud' }],
});
