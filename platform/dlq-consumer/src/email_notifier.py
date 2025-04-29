import smtplib
from email.mime.text import MIMEText
import logging
from .config import Config
from .metrics import EMAILS_SENT

logger = logging.getLogger(__name__)

def send_email_notification(subject: str, body: str, payload: dict = None, email_recipients: list = None):
    try:
        if not payload:
            payload = {
                "topic": "unknown",
                "connector": "unknown",
                "exception_class": "unknown",
                "transform_class": "unknown",
                "exception_message": body,
                "stacktrace": ""
            }

        
        # Construct HTML body
        html_body = f"""
        <html>
        <head></head>
        <body>
            <h2 style="color:#c0392b;">🚨 DLQ Alert - Kafka Connect</h2>
            <table border="1" cellpadding="8" cellspacing="0" style="border-collapse:collapse; font-family:Arial, sans-serif; font-size:14px;">
                <tr><th align="left">Topic</th><td>{payload.get('topic')}</td></tr>
                <tr><th align="left">Connector</th><td>{payload.get('connector')}</td></tr>
                <tr><th align="left">Exception Class</th><td>{payload.get('exception_class')}</td></tr>
                <tr><th align="left">Transform Class</th><td>{payload.get('transform_class')}</td></tr>
                <tr><th align="left">Exception Message</th><td>{payload.get('exception_message')}</td></tr>
                <tr><th align="left">Stacktrace (truncated)</th><td><pre>{payload.get('stacktrace')}</pre></td></tr>
            </table>
        </body>
        </html>
        """

        msg = MIMEText(html_body, "html")
        msg['Subject'] = subject
        msg['From'] = Config.EMAIL_FROM
        msg['To'] = ", ".join(email_recipients)  # Use the resolved email recipients

        # Uncomment the following lines to enable email sending
        with smtplib.SMTP(Config.EMAIL_SMTP_SERVER, Config.EMAIL_SMTP_PORT) as server:
            server.sendmail(Config.EMAIL_FROM, email_recipients, msg.as_string())

        logger.info(f"Email sent to {email_recipients} via {Config.EMAIL_SMTP_SERVER}")
        EMAILS_SENT.inc()

    except Exception as e:
        logger.error(f"Error sending DLQ email: {e}")