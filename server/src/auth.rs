//! Shared-token authentication for the API (bearer header) and admin UI (session cookie).

use hmac::{Hmac, Mac};
use sha2::Sha256;
use subtle::ConstantTimeEq;

#[derive(Clone)]
pub struct Auth {
    token: String,
    session_value: String,
}

impl Auth {
    pub fn new(token: String) -> Auth {
        // The admin cookie carries a value derived from the token, never the token itself.
        let mut mac =
            Hmac::<Sha256>::new_from_slice(token.as_bytes()).expect("hmac accepts any key");
        mac.update(b"howlongtocook-admin-session-v1");
        let session_value = hex::encode(mac.finalize().into_bytes());
        Auth {
            token,
            session_value,
        }
    }

    pub fn check_token(&self, candidate: &str) -> bool {
        candidate.as_bytes().ct_eq(self.token.as_bytes()).into()
    }

    pub fn check_bearer(&self, header: Option<&str>) -> bool {
        header
            .and_then(|h| {
                h.strip_prefix("Bearer ")
                    .or_else(|| h.strip_prefix("bearer "))
            })
            .is_some_and(|t| self.check_token(t.trim()))
    }

    pub fn session_value(&self) -> &str {
        &self.session_value
    }

    pub fn check_session(&self, candidate: &str) -> bool {
        candidate
            .as_bytes()
            .ct_eq(self.session_value.as_bytes())
            .into()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bearer_and_session() {
        let auth = Auth::new("0123456789abcdef".into());
        assert!(auth.check_bearer(Some("Bearer 0123456789abcdef")));
        assert!(!auth.check_bearer(Some("Bearer wrong")));
        assert!(!auth.check_bearer(Some("0123456789abcdef")));
        assert!(!auth.check_bearer(None));
        assert!(auth.check_session(auth.session_value()));
        assert!(!auth.session_value().contains("0123456789abcdef"));
    }
}
