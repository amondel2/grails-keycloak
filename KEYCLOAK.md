# Keycloak Setup

This document walks through configuring Keycloak for the Grails Keycloak Example application.

The example uses:

* Keycloak
* PostgreSQL
* OpenID Connect
* Client roles
* `USER` and `ADMIN` roles
* `ADMIN` inheriting from `USER`
* A client-role mapper
* Email verification
* Password reset
* Brevo for transactional email

The instructions are intended for a Linux installation of Keycloak. The paths and ports used below are examples and can be changed to match your environment.

---

# 1. Prerequisites

Before configuring Keycloak, you should have:

* Java installed
* Keycloak installed
* PostgreSQL installed
* The Grails example application
* A Brevo account if you want to test email verification and password reset

This guide assumes:

```text
Keycloak:
http://localhost:8081

Grails application:
http://localhost:8080

PostgreSQL:
localhost:5432
```

If you use different ports, replace them throughout the examples.

---

# 2. Configure PostgreSQL

Keycloak needs a relational database to store its realms, clients, users, roles, and other configuration.

This example uses PostgreSQL with a dedicated `keycloak` schema.

The Grails application does not require PostgreSQL; it uses an embedded H2 in-memory database. Only Keycloak uses PostgreSQL.

For example:

```text
PostgreSQL
└── keycloak
    └── Keycloak tables
```

Using a dedicated database or schema keeps Keycloak's tables separate from anything else on the PostgreSQL server.

Keycloak officially supports PostgreSQL and provides a PostgreSQL database driver as part of the standard distribution.

## 2.1 Create the PostgreSQL User

Connect to PostgreSQL as an administrator:

```bash
psql -U postgres
```

Create a dedicated user for Keycloak:

```sql
CREATE USER keycloak WITH PASSWORD '<your-keycloak-password>';
```

Use a strong password.

Do not commit the password to Git.

---

## 2.2 Create the Database

Create a dedicated database for Keycloak:

```sql
CREATE DATABASE example OWNER keycloak;
```

For the remainder of this guide, the example uses a database named:

```text
example
```

Only Keycloak uses this database; the Grails application does not require it.

---

## 2.3 Create the Keycloak Schema

Connect to the database:

```bash
psql -U postgres -d example
```

Create the Keycloak schema:

```sql
CREATE SCHEMA keycloak AUTHORIZATION keycloak;
```

Grant the Keycloak user the required access:

```sql
GRANT USAGE, CREATE ON SCHEMA keycloak TO keycloak;
```

If the schema already exists:

```sql
ALTER SCHEMA keycloak OWNER TO keycloak;
```

Keycloak must be able to create and modify its own tables in this schema.

Do not manually create the Keycloak tables. Keycloak manages its own database schema and migrations.

---

# 3. Configure Keycloak's Environment File

The database password should not be placed directly into the Keycloak startup command or committed to source control.

A simple approach is to create an environment file containing the Keycloak configuration.

For example, if Keycloak is installed at:

```text
/opt/keycloak
```

create:

```text
/opt/keycloak/keycloak.env
```

The location is not important. Choose a location that makes sense for your installation.

The file should contain:

```bash
KC_DB=postgres
KC_DB_URL=jdbc:postgresql://localhost:5432/example
KC_DB_SCHEMA=keycloak
KC_DB_USERNAME=keycloak
KC_DB_PASSWORD=<your-keycloak-password>
```

These are the environment-variable equivalents of Keycloak's database configuration options.

### Configuration explained

| Variable         | Description                         |
| ---------------- | ----------------------------------- |
| `KC_DB`          | Database vendor                     |
| `KC_DB_URL`      | Full PostgreSQL JDBC connection URL |
| `KC_DB_SCHEMA`   | PostgreSQL schema used by Keycloak  |
| `KC_DB_USERNAME` | PostgreSQL user                     |
| `KC_DB_PASSWORD` | PostgreSQL password                 |

For the example above:

```text
Database:
example

Host:
localhost

Port:
5432

Schema:
keycloak

User:
keycloak
```

The resulting JDBC URL is:

```text
jdbc:postgresql://localhost:5432/example
```

Keycloak's configuration documentation confirms that `KC_DB_URL` can be used to provide the complete JDBC URL and `KC_DB_SCHEMA` can override the database's default schema.

---

# 4. Protect the Environment File

The environment file contains a database password.

It should not be committed to Git.

For example:

```bash
sudo chmod 600 /opt/keycloak/keycloak.env
```

Make sure the file is readable by the account that runs Keycloak.

For example, if Keycloak runs as a user named `keycloak`:

```bash
sudo chown keycloak:keycloak /opt/keycloak/keycloak.env
```

The public example repository should **not** contain this file with a real password.

Instead, you can provide an example file such as:

```text
keycloak.env.example
```

containing:

```bash
KC_DB=postgres
KC_DB_URL=jdbc:postgresql://localhost:5432/example
KC_DB_SCHEMA=keycloak
KC_DB_USERNAME=keycloak
KC_DB_PASSWORD=<your-keycloak-password>
```

Users can copy it:

```bash
cp keycloak.env.example keycloak.env
```

and then replace the placeholder password.

Add the real file to `.gitignore`:

```gitignore
keycloak.env
```

---

# 5. Test the Environment File

Before creating a service, it is useful to verify that Keycloak can start using the environment file.

From the Keycloak installation directory:

```bash
set -a
source ./keycloak.env
set +a
```

The `set -a` command causes variables defined by the file to be exported to child processes.

Verify that the variables are available:

```bash
echo "$KC_DB"
echo "$KC_DB_URL"
echo "$KC_DB_SCHEMA"
echo "$KC_DB_USERNAME"
```

Do **not** echo the password.

You should see:

```text
postgres
jdbc:postgresql://localhost:5432/example
keycloak
keycloak
```

Then start Keycloak:

```bash
bin/kc.sh start-dev --http-port=8081
```

If the database configuration is correct, Keycloak should connect to PostgreSQL and initialize its database schema.

---

# 6. Verify the PostgreSQL Schema

After Keycloak has successfully started, you can verify that it created its tables.

Connect to PostgreSQL:

```bash
psql -U postgres -d example
```

List the schemas:

```sql
\dn
```

You should see:

```text
keycloak
```

Then list the tables:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'keycloak'
ORDER BY table_name;
```

You should see a number of Keycloak tables.

The exact table names and number of tables depend on the Keycloak version.

---

# 7. Create a systemd Service

Running Keycloak manually from a terminal works for testing, but it is much better to run it as a system service.

A systemd service will:

* Start Keycloak automatically
* Restart it if it exits unexpectedly
* Start it when the machine boots
* Load the database configuration automatically
* Keep Keycloak running independently of a terminal session

The following example assumes:

```text
Keycloak installation:
/opt/keycloak

Keycloak user:
keycloak

Environment file:
/opt/keycloak/keycloak.env
```

Change these values if your installation uses different paths or usernames.

---

# 8. Create the Keycloak Service User

If you do not already have a dedicated Linux user for Keycloak, create one:

```bash
sudo useradd --system --home /opt/keycloak --shell /usr/sbin/nologin keycloak
```

Make sure the Keycloak installation is accessible by this user:

```bash
sudo chown -R keycloak:keycloak /opt/keycloak
```

---

# 9. Create the systemd Service

Create:

```text
/etc/systemd/system/keycloak.service
```

with:

```ini
[Unit]
Description=Keycloak Identity and Access Management
After=network.target postgresql.service
Wants=postgresql.service

[Service]
Type=simple
User=keycloak
Group=keycloak

EnvironmentFile=/opt/keycloak/keycloak.env

WorkingDirectory=/opt/keycloak

ExecStart=/opt/keycloak/bin/kc.sh start-dev --http-port=8081

Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

The important part for this example is:

```ini
EnvironmentFile=/opt/keycloak/keycloak.env
```

This causes systemd to load the PostgreSQL configuration before starting Keycloak.

You do not need to run `source keycloak.env` when using systemd.

---

# 10. Enable the Keycloak Service

After creating the service:

```bash
sudo systemctl daemon-reload
```

Enable it so that it starts automatically:

```bash
sudo systemctl enable keycloak
```

Start it:

```bash
sudo systemctl start keycloak
```

Check the status:

```bash
sudo systemctl status keycloak
```

You should see that the service is running.

---

# 11. View Keycloak Logs

If Keycloak does not start, the first place to look is the systemd journal:

```bash
sudo journalctl -u keycloak
```

To follow the logs while Keycloak starts:

```bash
sudo journalctl -u keycloak -f
```

For example, database connection problems may show up here.

Common problems include:

* PostgreSQL is not running
* Incorrect database name
* Incorrect PostgreSQL port
* Incorrect database username
* Incorrect password
* The `keycloak` schema does not exist
* The Keycloak user does not have permission to use the schema

---

# 12. Start and Stop Keycloak

Once the service is configured, use systemd to manage Keycloak.

Start:

```bash
sudo systemctl start keycloak
```

Stop:

```bash
sudo systemctl stop keycloak
```

Restart:

```bash
sudo systemctl restart keycloak
```

Check status:

```bash
sudo systemctl status keycloak
```

View logs:

```bash
sudo journalctl -u keycloak -f
```

---

# 13. Access the Keycloak Administration Console

Once Keycloak is running, open:

```text
http://localhost:8081
```

Log into the administration console using the Keycloak administrator account.

If you are accessing Keycloak from another machine, replace `localhost` with the hostname or address where Keycloak is running.

---

# Realm Configuration

# 14. Create the Example Realm

From the Keycloak Administration Console, create a new realm.

Use:

```text
Realm name:
example
```

The OpenID Connect issuer for the realm will be:

```text
http://localhost:8081/realms/example
```

This value will be used by the Grails application:

```bash
KEYCLOAK_ISSUER=http://localhost:8081/realms/example
```

---

# Client Configuration

# 15. Create the Client

Navigate to:

**Clients → Create client**

Create an OpenID Connect client.

Use:

```text
Client type:
OpenID Connect

Client ID:
example-client
```

The Grails application will use:

```bash
KEYCLOAK_CLIENT_ID=example-client
```

---

# 16. Configure Client Authentication

Enable client authentication.

Keycloak will generate a client secret.

The Grails application will use this value as:

```bash
KEYCLOAK_CLIENT_SECRET=<client-secret>
```

Do not commit the client secret to Git.

---

# 17. Configure Redirect URIs

For the example application running locally on port `8080`, configure:

```text
http://localhost:8080/login/oauth2/code/keycloak
```

This is the OAuth2 login callback used by the Grails application.

If you run the application on a different URL, change the redirect URI accordingly.

---

# 18. Configure Web Origins

For local development, configure:

```text
http://localhost:8080
```

as the web origin.

Do not use unrestricted origins unless they are specifically required.

---

# Client Roles

# 19. Create the USER Client Role

This example uses **client roles** rather than realm roles.

Navigate to:

**Clients → example-client → Roles**

Create:

```text
USER
```

Do not create this under **Realm roles**.

The application uses this role for normal authenticated users.

For example:

```groovy
@PreAuthorize("hasRole('ROLE_USER')")
```

The Keycloak role is:

```text
USER
```

and the Spring Security authority is:

```text
ROLE_USER
```

---

# 20. Create the ADMIN Client Role

Under:

**Clients → example-client → Roles**

create:

```text
ADMIN
```

The client should now contain:

```text
USER
ADMIN
```

The application can protect administrator-only functionality with:

```groovy
@PreAuthorize("hasRole('ROLE_ADMIN')")
```

---

# 21. Make ADMIN Inherit USER

Keycloak supports composite roles.

Configure `ADMIN` as a composite role containing `USER`.

Navigate to:

**Clients → example-client → Roles → ADMIN**

Add `USER` as an associated/composite role.

The resulting structure is:

```text
example-client
│
├── USER
│
└── ADMIN
    └── USER
```

A user assigned `USER` has:

```text
USER
```

A user assigned `ADMIN` has:

```text
ADMIN
USER
```

The administrator does not need to be assigned `USER` separately.

This allows the application to use:

```groovy
@PreAuthorize("hasRole('ROLE_USER')")
```

for functionality available to both users and administrators, while:

```groovy
@PreAuthorize("hasRole('ROLE_ADMIN')")
```

can be used for administrator-only functionality.

---

# Client Role Mapper

# 22. Add the Client Role Mapper

Creating the roles is not enough.

The client roles must also be included in the OpenID Connect tokens used by the Grails application.

Navigate to:

**Clients → example-client → Client scopes → Dedicated scopes**

Add a mapper for the client roles.

Configure the mapper for the `example-client` client.

This mapper exposes the client's roles to the tokens used by the application.

---

# 23. Configure the Mapper

Configure the mapper with the following settings:

| Setting                         | Value   |
| ------------------------------- | ------- |
| Add to ID token                 | **On**  |
| Add to access token             | **On**  |
| Add to lightweight access token | **Off** |
| Add to userinfo                 | **On**  |
| Add to token introspection      | **On**  |

The final configuration should be:

```text
Add to ID token:                  On
Add to access token:              On
Add to lightweight access token:  Off
Add to userinfo:                  On
Add to token introspection:       On
```

---

# 24. Verify the Token with Evaluate

Before troubleshooting the Grails application, verify that Keycloak is actually putting the roles into the token.

Navigate to:

**Clients → example-client → Client scopes → Evaluate**

Select a test user.

The Evaluate screen allows you to inspect the token information Keycloak would issue for that user.

For a `USER`, the token should contain:

```text
USER
```

For an `ADMIN`, the effective roles should contain:

```text
ADMIN
USER
```

The client roles are typically represented in the token under `resource_access`.

For example:

```json
{
  "resource_access": {
    "example-client": {
      "roles": [
        "USER"
      ]
    }
  }
}
```

An administrator should have something similar to:

```json
{
  "resource_access": {
    "example-client": {
      "roles": [
        "ADMIN",
        "USER"
      ]
    }
  }
}
```

The exact token output can vary by Keycloak version and configuration.

### Why Evaluate is important

If the roles are missing from **Evaluate**, the problem is in Keycloak.

Check:

* The roles are client roles.
* The roles belong to `example-client`.
* The user has the expected role.
* `ADMIN` contains `USER`.
* The client-role mapper exists.
* The mapper is configured for `example-client`.
* **Add to ID token** is On.
* **Add to access token** is On.
* **Add to lightweight access token** is Off.
* **Add to userinfo** is On.
* **Add to token introspection** is On.

If the roles are present in **Evaluate** but the Grails application does not recognize them, the problem is likely in the application's authority mapping.

After changing roles or mapper settings, log out and log back in so that a new token is issued.

---

# Users

# 25. Create a Test User

Navigate to:

**Users → Add user**

Create:

```text
Username:
test-user

Email:
test-user@example.com
```

Set a password under **Credentials**.

---

# 26. Assign USER

Open the user and navigate to:

**Role mapping**

Select the `example-client` client roles.

Assign:

```text
USER
```

The user should have:

```text
Direct role:
USER

Effective roles:
USER
```

---

# 27. Create an Administrator

Create another user:

```text
Username:
test-admin

Email:
test-admin@example.com
```

Assign:

```text
ADMIN
```

Do not separately assign `USER`.

Because `ADMIN` contains `USER`, the effective roles should be:

```text
Direct role:
ADMIN

Effective roles:
ADMIN
USER
```

Use **Client scopes → Evaluate** to verify this.

---

# Email Configuration

This example uses **Brevo** for transactional email.

Keycloak can use Brevo's SMTP service to send:

* Email verification messages
* Password-reset messages
* Other account-related emails

Brevo provides a free plan suitable for a small example application.

---

# 28. Create a Brevo Account

Create a Brevo account:

https://www.brevo.com/

Enable transactional email.

Brevo provides the SMTP relay:

```text
smtp-relay.brevo.com
```

Use port:

```text
587
```

for TLS/STARTTLS.

---

# 29. Authenticate Your Domain

In Brevo, configure the domain you will use as the sender.

For example:

```text
noreply@example.com
```

would use:

```text
example.com
```

Brevo will provide the DNS records required to authenticate the domain.

These may include:

* Domain verification
* DKIM
* SPF

Use the exact records provided by Brevo.

Do not copy DNS records from this example.

---

# 30. Create a Brevo Sender

Create a sender in Brevo.

For example:

```text
From name:
Example Application

From email:
noreply@example.com
```

The sender should be verified by Brevo or belong to an authenticated domain.

---

# 31. Get Brevo SMTP Credentials

In Brevo, go to:

**Settings → SMTP & API**

Obtain the SMTP credentials.

You will need:

```text
SMTP server:
smtp-relay.brevo.com

SMTP port:
587

SMTP login:
<Brevo SMTP login>

SMTP key:
<Brevo SMTP key>
```

The SMTP key is used as the SMTP password.

Do not use a Brevo API key as the SMTP password.

Do not commit the SMTP key to Git.

---

# 32. Configure Keycloak Email

In Keycloak:

**Realm settings → Email**

Configure:

```text
From:
noreply@example.com

Host:
smtp-relay.brevo.com

Port:
587

Authentication:
On

Username:
<Brevo SMTP login>

Password:
<Brevo SMTP key>
```

Use the TLS/STARTTLS option associated with port `587`.

---

# 33. Enable Email Verification

Navigate to:

**Realm settings → Login**

Enable:

```text
Verify email
```

Create a test user with a valid email address and verify that Keycloak sends the verification email.

---

# 34. Test Password Reset

Open the Keycloak login page.

Select:

**Forgot Password?**

Enter the test user's email address.

Verify that the password-reset email arrives.

Then:

1. Open the email.
2. Follow the reset link.
3. Set a new password.
4. Log in using the new password.

---

# Grails Configuration

# 35. Configure the Grails Environment Variables

The Grails application needs the following Keycloak settings:

```bash
export KEYCLOAK_CLIENT_ID=example-client
export KEYCLOAK_CLIENT_SECRET=<client-secret>
export KEYCLOAK_ISSUER=http://localhost:8081/realms/example
```

These values correspond to:

```text
Realm:
example

Client:
example-client

Issuer:
http://localhost:8081/realms/example
```

The client secret is the secret generated by Keycloak when client authentication was enabled.

---

# 36. Test the Application

Start the Grails application with the Keycloak environment variables configured.

Test the following.

## Anonymous User

Visit a public page.

It should be accessible without logging in.

## Normal User

Log in as:

```text
test-user
```

The user should be able to access functionality protected by:

```groovy
@PreAuthorize("hasRole('ROLE_USER')")
```

The user should not be able to access functionality protected by:

```groovy
@PreAuthorize("hasRole('ROLE_ADMIN')")
```

## Administrator

Log in as:

```text
test-admin
```

The administrator should be able to access both:

```text
USER functionality
ADMIN functionality
```

This confirms that the composite role is working.

---

# Troubleshooting

## Keycloak cannot connect to PostgreSQL

Check:

```bash
sudo systemctl status postgresql
```

Then verify the database connection manually:

```bash
psql -U keycloak -d example -h localhost
```

Check the environment file:

```bash
cat /path/to/keycloak.env
```

Do not share the password when asking for help.

Verify:

```text
KC_DB=postgres
KC_DB_URL=jdbc:postgresql://localhost:5432/example
KC_DB_SCHEMA=keycloak
KC_DB_USERNAME=keycloak
KC_DB_PASSWORD=<correct-password>
```

Also verify that the PostgreSQL user owns or has sufficient permissions on the `keycloak` schema.

---

## Keycloak starts manually but not with systemd

Check the service:

```bash
sudo systemctl status keycloak
```

Then inspect the logs:

```bash
sudo journalctl -u keycloak -n 100
```

Common causes include:

* Incorrect `EnvironmentFile` path
* Incorrect `User`
* Incorrect `Group`
* Keycloak installation permissions
* Environment file permissions
* Incorrect Java configuration
* PostgreSQL unavailable

Verify that the service contains:

```ini
EnvironmentFile=/opt/keycloak/keycloak.env
```

and that the path matches the actual location of your environment file.

---

## Keycloak is not using the environment variables

Remember that Keycloak supports several configuration sources, and command-line options have higher precedence than environment variables.

Check the service configuration for command-line arguments that override your environment variables.

You can also check Keycloak's available configuration with:

```bash
bin/kc.sh start --help
```

---

## Roles are missing from the application

Start with Keycloak rather than Grails.

Go to:

**Clients → example-client → Client scopes → Evaluate**

Select the affected user and inspect the token.

If the roles are missing there, check the Keycloak role and mapper configuration.

If the roles appear correctly in Evaluate but the application does not recognize them, investigate the Grails/Spring Security authority mapping.

---

## ADMIN does not have USER permissions

Verify that:

```text
ADMIN
└── USER
```

is configured as a composite role.

Then use:

**Clients → example-client → Client scopes → Evaluate**

to verify that an administrator has both:

```text
ADMIN
USER
```

as effective roles.

---

## Redirect URI errors

Verify that the Keycloak client contains:

```text
http://localhost:8080/login/oauth2/code/keycloak
```

The URI must match the application's actual URL.

---

## Email does not arrive

Check:

* Brevo sender configuration
* Domain authentication
* DNS records
* SMTP hostname
* SMTP port
* SMTP username
* SMTP key
* Keycloak email configuration
* Keycloak logs
* Brevo transactional email logs

A common mistake is using the Brevo API key instead of the SMTP key.

---

# Final Configuration

The complete example looks like:

```text
PostgreSQL
│
└── example
    │
    └── keycloak
        └── Keycloak tables
```

```text
Keycloak
│
└── example realm
    │
    └── example-client
        │
        ├── USER
        │
        └── ADMIN
            └── USER
        │
        └── Client Role Mapper
            ├── ID Token: On
            ├── Access Token: On
            ├── Lightweight Access Token: Off
            ├── Userinfo: On
            └── Token Introspection: On
```

Email:

```text
Keycloak
    │
    │ SMTP
    ▼
Brevo
    │
    ▼
User's Email
```

Authentication:

```text
Grails
    │
    │ OpenID Connect
    ▼
Keycloak
```

Authorization:

```text
ADMIN
  │
  └── USER
```

Therefore:

```text
USER
→ USER permissions

ADMIN
→ ADMIN permissions
→ USER permissions
```

The most important configuration pieces are:

1. PostgreSQL provides persistent storage for Keycloak.
2. Keycloak is configured through environment variables.
3. A systemd service loads those variables automatically.
4. `example` is the Keycloak realm.
5. `example-client` represents the Grails application.
6. `USER` and `ADMIN` are **client roles**.
7. `ADMIN` is a composite role containing `USER`.
8. A client-role mapper places the roles into the OIDC tokens.
9. The **Evaluate** screen verifies the token before troubleshooting Grails.
10. Spring Security maps the Keycloak roles to authorities such as `ROLE_USER` and `ROLE_ADMIN`.
11. `@PreAuthorize` controls access within the Grails application.
12. Brevo provides SMTP delivery for Keycloak's verification and password-reset emails.
