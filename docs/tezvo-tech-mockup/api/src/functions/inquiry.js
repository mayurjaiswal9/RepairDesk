const { app } = require("@azure/functions");
const { EmailClient } = require("@azure/communication-email");

const TO = process.env.INQUIRY_TO || "support@tezvo-tech.com";
const SENDER = process.env.ACS_SENDER || "";
const CONNECTION = process.env.ACS_CONNECTION_STRING || "";

function esc(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

app.http("inquiry", {
  methods: ["POST", "OPTIONS"],
  authLevel: "anonymous",
  handler: async (request, context) => {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "content-type",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
    };

    if (request.method === "OPTIONS") {
      return { status: 204, headers: cors };
    }

    if (!CONNECTION || !SENDER) {
      context.error("ACS_CONNECTION_STRING or ACS_SENDER is not configured");
      return {
        status: 500,
        headers: { ...cors, "Content-Type": "application/json" },
        jsonBody: { ok: false, message: "Email is not configured yet." },
      };
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return {
        status: 400,
        headers: { ...cors, "Content-Type": "application/json" },
        jsonBody: { ok: false, message: "Invalid request." },
      };
    }

    // Honeypot — bots fill this hidden field.
    if (body.company_website) {
      return {
        status: 200,
        headers: { ...cors, "Content-Type": "application/json" },
        jsonBody: { ok: true },
      };
    }

    const name = String(body.name || "").trim();
    const email = String(body.email || "").trim();
    const company = String(body.company || "").trim();
    const need = String(body.need || "").trim();
    const message = String(body.message || "").trim();

    if (!name || !email || !message) {
      return {
        status: 400,
        headers: { ...cors, "Content-Type": "application/json" },
        jsonBody: { ok: false, message: "Name, email, and message are required." },
      };
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return {
        status: 400,
        headers: { ...cors, "Content-Type": "application/json" },
        jsonBody: { ok: false, message: "Please enter a valid email address." },
      };
    }

    const subject = `New inquiry from ${name}${need ? " — " + need : ""}`;
    const html = `
      <div style="font-family:Segoe UI,Arial,sans-serif;line-height:1.5;color:#17130f">
        <h2 style="margin:0 0 12px">New TEZVO TECH website inquiry</h2>
        <p><b>Name:</b> ${esc(name)}</p>
        <p><b>Email:</b> ${esc(email)}</p>
        <p><b>Company:</b> ${esc(company) || "—"}</p>
        <p><b>Need:</b> ${esc(need) || "—"}</p>
        <p><b>Message:</b></p>
        <p style="white-space:pre-wrap">${esc(message)}</p>
      </div>
    `;

    try {
      const client = new EmailClient(CONNECTION);
      const poller = await client.beginSend({
        senderAddress: SENDER,
        replyTo: [{ address: email, displayName: name }],
        content: {
          subject,
          html,
          plainText: `Name: ${name}\nEmail: ${email}\nCompany: ${company}\nNeed: ${need}\n\n${message}`,
        },
        recipients: { to: [{ address: TO }] },
      });
      await poller.pollUntilDone();
      return {
        status: 200,
        headers: { ...cors, "Content-Type": "application/json" },
        jsonBody: { ok: true },
      };
    } catch (err) {
      context.error("Failed to send inquiry email", err);
      return {
        status: 502,
        headers: { ...cors, "Content-Type": "application/json" },
        jsonBody: { ok: false, message: "Could not send your message. Please email support@tezvo-tech.com." },
      };
    }
  },
});
