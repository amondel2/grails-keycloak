# Grails + Keycloak Example

A simple example application demonstrating how to integrate [Grails](https://grails.apache.org/) with [Keycloak](https://www.keycloak.org/) for authentication and role-based authorization.

The goal of this project is to provide a starting point for a Grails application that needs:

* Keycloak authentication using OpenID Connect
* Login and logout
* Role-based authorization
* `ADMIN` and `USER` roles
* Composite roles and inherited permissions
* Embedded H2 database (no external database required for the Grails application)
* Email verification
* Password reset
* Environment-based configuration

This project intentionally uses generic example names and values so that it can be used as a starting point for another application.

## Architecture

The example consists of three primary components:

```text
                 ┌──────────────┐
                 │    Browser   │
                 └──────┬───────┘
                        │
                        ▼
                 ┌──────────────┐
                 │ Grails App   │
                 │              │
                 │ Spring       │
                 │ Security     │
                 └──────┬───────┘
                        │
                  OpenID Connect
                        │
                        ▼
                 ┌──────────────┐
                 │   Keycloak   │
                 │              │
                 │   example    │
                 │    realm     │
                 └──────┬───────┘
                        │
                        ▼
                 ┌──────────────┐
                 │  PostgreSQL  │
                 └──────────────┘

                 Keycloak
                    │
                    ▼
                 SMTP Email
```

Keycloak stores its configuration (realms, clients, users, roles) in PostgreSQL.

The Grails application uses an embedded H2 in-memory database and does not require a separate database. It persists nothing itself.

## Requirements

Before starting, install:

* Java 21
* Grails 8.x
* PostgreSQL (used by Keycloak)
* Keycloak 26.x

The versions used by this example may change over time. Check the project configuration for the exact Grails version used by the current example.

---

# 1. PostgreSQL Setup

Only Keycloak requires PostgreSQL.

The Grails application uses an embedded H2 in-memory database, so no database setup is needed for it.

To set up PostgreSQL for Keycloak, create a dedicated database and user:

```sql
CREATE USER keycloak WITH PASSWORD 'change-me';

CREATE DATABASE keycloak
    OWNER keycloak;
```

Keycloak can then use:

```text
Database: keycloak
Username: keycloak
Password: change-me
```

The exact Keycloak database configuration is covered in [KEYCLOAK.md](KEYCLOAK.md).

---

# 2. Configure the Grails Application

Clone the repository:

```bash
git clone https://github.com/amondel2/grails-keycloak-example.git
cd grails-keycloak-example
```

The application reads its Keycloak configuration from environment variables.

The current `application.yml` uses the following variables:

```text
KEYCLOAK_CLIENT_ID
KEYCLOAK_CLIENT_SECRET
KEYCLOAK_ISSUER
```

The Keycloak configuration is intentionally externalized so that client secrets do not need to be stored in source control.

## Keycloak environment variables

After configuring Keycloak, set:

```bash
export KEYCLOAK_CLIENT_ID=example-client
export KEYCLOAK_CLIENT_SECRET=change-me
export KEYCLOAK_ISSUER=http://localhost:8081/realms/example
```

The values above are examples.

The important part is that `KEYCLOAK_ISSUER` points to the issuer for the realm created for this application.

For example:

```text
http://localhost:8081/realms/example
```

or, in a deployed environment:

```text
https://auth.example.com/realms/example
```

The exact URL depends on how Keycloak is deployed.

See [KEYCLOAK.md](KEYCLOAK.md) for the complete Keycloak configuration.

---

# 3. Run the Application

Once Keycloak is running and the environment variables have been configured:

```bash
./grailsw bootRun
```

The application should start on its configured development port.

Open the application in a browser.

Public pages should be accessible without authentication.

Protected pages will redirect the user to Keycloak.

After successfully authenticating with Keycloak, the user will be returned to the Grails application.

---

# 4. Keycloak Roles

This example uses two realm roles:

```text
USER
ADMIN
```

The `USER` role represents normal authenticated application users.

The `ADMIN` role represents administrators.

The application can use Spring Security annotations such as:

```groovy
@PreAuthorize("hasRole('ROLE_USER')")
```

and:

```groovy
@PreAuthorize("hasRole('ROLE_ADMIN')")
```

## Role inheritance

Keycloak supports composite roles.

For this example, `ADMIN` should inherit `USER`.

The resulting relationship is:

```text
ADMIN
  │
  └── USER
```

A user assigned only:

```text
ADMIN
```

will therefore have the effective permissions of:

```text
ADMIN
USER
```

This is useful because application code can protect normal functionality with `ROLE_USER` while administrator functionality can require `ROLE_ADMIN`.

For example:

```groovy
@PreAuthorize("hasRole('ROLE_USER')")
def account() {
    // Available to USER and ADMIN
}
```

```groovy
@PreAuthorize("hasRole('ROLE_ADMIN')")
def administration() {
    // Available only to ADMIN
}
```

The complete Keycloak role configuration is documented in [KEYCLOAK.md](KEYCLOAK.md).

Keycloak calls this type of role a **composite role**. Composite roles cause the associated roles to become effective roles for a user assigned the composite role.

---

# 5. Email Configuration

Keycloak can send email for functionality such as:

* Email verification
* Password resets
* Account-related notifications

Keycloak uses an SMTP server for this functionality.

This example uses an external SMTP email service rather than requiring you to operate your own mail server.

## SMTP service

The example deployment used a free outbound-only SMTP service.

You will need to create an account with an SMTP provider and obtain:

```text
SMTP Host
SMTP Port
SMTP Username
SMTP Password
From Address
```

Do not commit SMTP credentials to Git.

Use the SMTP credentials provided by your email service when configuring Keycloak.

For example:

```text
SMTP Host: smtp.example-mail-service.com
SMTP Port: 587
Username: example@example.com
Password: <SMTP password>
From: noreply@example.com
```

The values above are examples only.

### Domain configuration

If the email provider requires domain verification, add the DNS records it provides to your domain's DNS configuration.

These commonly include SPF and DKIM records.

If your DNS is managed through Cloudflare, add the records provided by your email provider to the appropriate Cloudflare DNS zone.

The SMTP configuration itself belongs in Keycloak rather than in the Grails application.

See [KEYCLOAK.md](KEYCLOAK.md) for configuring Keycloak's email settings and enabling email verification and password reset.

---

# 6. Testing Authentication

After completing the setup, test the following:

### Anonymous user

Visit a public page.

The page should be accessible without logging in.

### Protected page

Visit a page protected by authentication.

You should be redirected to Keycloak.

After logging in, Keycloak should redirect you back to the Grails application.

### USER

Create a Keycloak user and assign:

```text
USER
```

The user should be able to access functionality protected by:

```groovy
@PreAuthorize("hasRole('ROLE_USER')")
```

The user should not be able to access functionality protected by:

```groovy
@PreAuthorize("hasRole('ROLE_ADMIN')")
```

### ADMIN

Assign:

```text
ADMIN
```

to another user.

Because `ADMIN` is configured as a composite role containing `USER`, that user should have both effective roles:

```text
ADMIN
USER
```

They should therefore be able to access both USER and ADMIN functionality.

---

# 7. Email Testing

After configuring SMTP in Keycloak:

1. Create a test user with a valid email address.
2. Enable email verification.
3. Trigger the verification email.
4. Confirm that the email is received.
5. Log out.
6. Use the **Forgot Password** functionality.
7. Confirm that the password-reset email is received.
8. Follow the link and set a new password.

Keycloak's login settings provide a **Forgot Password** option when it is enabled, and the email configuration must have a valid SMTP host and From address.

---

# 8. Environment Variables Summary

For local development, the complete environment might look like:

```bash
export KEYCLOAK_CLIENT_ID=example-client
export KEYCLOAK_CLIENT_SECRET=change-me
export KEYCLOAK_ISSUER=http://localhost:8081/realms/example
```

The Grails application does not require any database environment variables; it uses an embedded H2 in-memory database.

These are example values.

Never commit real passwords, client secrets, SMTP passwords, or other credentials to the repository.

---

# 9. Keycloak Setup

The Keycloak configuration is intentionally documented separately because there are several steps involved in creating the realm, client, roles, role inheritance, and email configuration.

Continue with:

**[KEYCLOAK.md](KEYCLOAK.md)**

---

# 10. Useful Links

* [Grails Documentation](https://grails.apache.org/)
* [Keycloak Documentation](https://www.keycloak.org/documentation)
* [PostgreSQL Documentation](https://www.postgresql.org/docs/)

## License

See the repository license for details.
