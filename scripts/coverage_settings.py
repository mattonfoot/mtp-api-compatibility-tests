"""Custom Django settings module for compatibility-test coverage runs.

Imports the project's base settings then monkey-patches `mtp_common.notify.NotifyClient`
so user-creation endpoints don't 500 trying to talk to GOV.UK Notify. We don't touch
the Python project's source tree — we just point DJANGO_SETTINGS_MODULE at this file.
"""
from mtp_api.settings.base import *  # noqa: F401,F403


def _install_notify_stub():
    from mtp_common.notify import client as _client

    class _StubNotifyClient:
        @classmethod
        def shared_client(cls):
            return cls()

        def __init__(self):
            self._template_map = {}
            self.client = None
            self.reply_to_public = None
            self.reply_to_staff = None
            self.reply_to_default = None

        def get_template_id_for_name(self, template_name: str) -> str:
            return template_name

        @classmethod
        def can_send_email_to_address(cls, email_address: str) -> bool:
            return False

        def send_email(self, *args, **kwargs):
            return [None]

        def send_plain_text_email(self, *args, **kwargs):
            return [None]

    _client.NotifyClient = _StubNotifyClient

    import mtp_common.notify as _notify_pkg
    _notify_pkg.NotifyClient = _StubNotifyClient


_install_notify_stub()
