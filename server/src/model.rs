//! Canonical product model shared with the Android app (docs/DESIGN.md §5).

use serde::{Deserialize, Serialize};

pub const MAX_NAME_LEN: usize = 200;
pub const MAX_TEXT_LEN: usize = 4000;
pub const MAX_BARCODES: usize = 20;
pub const MAX_TAGS: usize = 20;
pub const MAX_TAG_LEN: usize = 40;
pub const MAX_METHODS: usize = 10;
pub const MAX_STEPS: usize = 50;
pub const MAX_DURATION_SECONDS: u32 = 86_400;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Product {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub brand: Option<String>,
    #[serde(default)]
    pub barcodes: Vec<String>,
    #[serde(default)]
    pub tags: Vec<String>,
    #[serde(default)]
    pub favorite: bool,
    #[serde(default)]
    pub rating: Option<u8>,
    #[serde(default)]
    pub notes: Option<String>,
    #[serde(default)]
    pub methods: Vec<CookingMethod>,
    pub created_at: i64,
    pub updated_at: i64,
    #[serde(default)]
    pub deleted: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum MethodType {
    Microwave,
    Oven,
    AirFryer,
    Stovetop,
}

impl MethodType {
    pub fn label(&self) -> &'static str {
        match self {
            MethodType::Microwave => "Microwave",
            MethodType::Oven => "Oven",
            MethodType::AirFryer => "Air fryer",
            MethodType::Stovetop => "Stovetop",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CookingMethod {
    #[serde(rename = "type")]
    pub method_type: MethodType,
    #[serde(default)]
    pub power: Option<String>,
    #[serde(default)]
    pub temperature: Option<Temperature>,
    #[serde(default)]
    pub preheat: bool,
    #[serde(default)]
    pub steps: Vec<Step>,
    #[serde(default)]
    pub notes: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TemperatureUnit {
    F,
    C,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Temperature {
    pub value: i32,
    pub unit: TemperatureUnit,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum StepAction {
    Cook,
    Stir,
    Flip,
    Shake,
    Stand,
    Vent,
    Remove,
    Other,
}

impl StepAction {
    pub fn label(&self) -> &'static str {
        match self {
            StepAction::Cook => "Cook",
            StepAction::Stir => "Stir",
            StepAction::Flip => "Flip",
            StepAction::Shake => "Shake",
            StepAction::Stand => "Stand",
            StepAction::Vent => "Vent",
            StepAction::Remove => "Remove",
            StepAction::Other => "Other",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Step {
    pub action: StepAction,
    #[serde(default)]
    pub duration_seconds: Option<u32>,
    #[serde(default)]
    pub note: Option<String>,
}

/// Whole-library document. Same shape as the `library.json` object used for S3/R2.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Library {
    pub schema_version: u32,
    #[serde(default)]
    pub updated_at: i64,
    pub products: Vec<Product>,
}

pub const SCHEMA_VERSION: u32 = 1;

impl Product {
    /// Validate and normalize (trim strings, drop empty optionals, dedupe lists).
    pub fn normalized(mut self) -> Result<Product, String> {
        self.id = self.id.trim().to_string();
        if uuid::Uuid::parse_str(&self.id).is_err() {
            return Err("id must be a UUID".into());
        }
        self.name = self.name.trim().to_string();
        if self.name.is_empty() || self.name.chars().count() > MAX_NAME_LEN {
            return Err(format!("name must be 1-{MAX_NAME_LEN} characters"));
        }
        self.brand = clean_opt(self.brand, MAX_NAME_LEN, "brand")?;
        self.notes = clean_opt(self.notes, MAX_TEXT_LEN, "notes")?;

        let mut barcodes: Vec<String> = Vec::new();
        for code in self.barcodes.iter().map(|c| c.trim()) {
            if code.is_empty() {
                continue;
            }
            if !is_valid_barcode(code) {
                return Err(format!("invalid barcode '{code}'"));
            }
            if !barcodes.iter().any(|c| c == code) {
                barcodes.push(code.to_string());
            }
        }
        if barcodes.len() > MAX_BARCODES {
            return Err(format!("at most {MAX_BARCODES} barcodes"));
        }
        self.barcodes = barcodes;

        let mut tags: Vec<String> = Vec::new();
        for tag in self.tags.iter().map(|t| t.trim()) {
            if tag.is_empty() {
                continue;
            }
            if tag.chars().count() > MAX_TAG_LEN {
                return Err(format!("tags must be at most {MAX_TAG_LEN} characters"));
            }
            if !tags.iter().any(|t| t.eq_ignore_ascii_case(tag)) {
                tags.push(tag.to_string());
            }
        }
        if tags.len() > MAX_TAGS {
            return Err(format!("at most {MAX_TAGS} tags"));
        }
        self.tags = tags;

        if let Some(r) = self.rating
            && !(1..=5).contains(&r)
        {
            return Err("rating must be between 1 and 5".into());
        }
        if self.methods.len() > MAX_METHODS {
            return Err(format!("at most {MAX_METHODS} methods"));
        }
        for method in &mut self.methods {
            method.power = clean_opt(method.power.take(), 60, "power")?;
            method.notes = clean_opt(method.notes.take(), MAX_TEXT_LEN, "method notes")?;
            if let Some(t) = &method.temperature
                && !(0..=1000).contains(&t.value)
            {
                return Err("temperature out of range".into());
            }
            if method.steps.len() > MAX_STEPS {
                return Err(format!("at most {MAX_STEPS} steps per method"));
            }
            for step in &mut method.steps {
                if let Some(d) = step.duration_seconds
                    && d > MAX_DURATION_SECONDS
                {
                    return Err("step duration too long".into());
                }
                step.note = clean_opt(step.note.take(), 500, "step note")?;
            }
        }
        if self.created_at < 0 || self.updated_at < 0 {
            return Err("timestamps must be positive".into());
        }
        Ok(self)
    }

    /// Short human summary of the preferred (first) method, e.g. "Microwave · 7 min".
    pub fn summary(&self) -> String {
        match self.methods.first() {
            None => "No instructions".into(),
            Some(m) => {
                let total: u32 = m.steps.iter().filter_map(|s| s.duration_seconds).sum();
                if total == 0 {
                    m.method_type.label().to_string()
                } else {
                    format!("{} · {}", m.method_type.label(), format_duration(total))
                }
            }
        }
    }
}

impl CookingMethod {
    /// e.g. "400°F · Preheat" or "Power High".
    pub fn detail(&self) -> String {
        let mut parts = Vec::new();
        if let Some(t) = &self.temperature {
            let unit = match t.unit {
                TemperatureUnit::F => "°F",
                TemperatureUnit::C => "°C",
            };
            parts.push(format!("{}{unit}", t.value));
        }
        if let Some(p) = &self.power {
            parts.push(format!("Power {p}"));
        }
        if self.preheat {
            parts.push("Preheat".into());
        }
        parts.join(" · ")
    }

    pub fn total_seconds(&self) -> u32 {
        self.steps.iter().filter_map(|s| s.duration_seconds).sum()
    }

    pub fn total_label(&self) -> String {
        match self.total_seconds() {
            0 => String::new(),
            t => format_duration(t),
        }
    }
}

impl Step {
    /// e.g. "Cook 4:00 — on a baking sheet".
    pub fn text(&self) -> String {
        let mut s = self.action.label().to_string();
        if let Some(d) = self.duration_seconds {
            s.push(' ');
            s.push_str(&format_clock(d));
        }
        if let Some(n) = &self.note {
            s.push_str(" — ");
            s.push_str(n);
        }
        s
    }
}

/// m:ss or h:mm:ss.
pub fn format_clock(seconds: u32) -> String {
    let (h, m, s) = (seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    if h > 0 {
        format!("{h}:{m:02}:{s:02}")
    } else {
        format!("{m}:{s:02}")
    }
}

pub fn is_valid_barcode(code: &str) -> bool {
    (4..=64).contains(&code.len()) && code.chars().all(|c| c.is_ascii_alphanumeric() || c == '-')
}

fn clean_opt(value: Option<String>, max: usize, field: &str) -> Result<Option<String>, String> {
    match value.map(|v| v.trim().to_string()) {
        None => Ok(None),
        Some(v) if v.is_empty() => Ok(None),
        Some(v) if v.chars().count() > max => {
            Err(format!("{field} must be at most {max} characters"))
        }
        Some(v) => Ok(Some(v)),
    }
}

pub fn format_duration(seconds: u32) -> String {
    let (h, m, s) = (seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    match (h, m, s) {
        (0, 0, s) => format!("{s} sec"),
        (0, m, 0) => format!("{m} min"),
        (0, m, s) => format!("{m}:{s:02}"),
        (h, 0, 0) => format!("{h} hr"),
        (h, m, _) => format!("{h} hr {m} min"),
    }
}

pub fn now_millis() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> Product {
        serde_json::from_str(include_str!("../../api/examples/product.json")).unwrap()
    }

    #[test]
    fn example_round_trips() {
        let p = sample();
        let json = serde_json::to_value(&p).unwrap();
        let again: Product = serde_json::from_value(json).unwrap();
        assert_eq!(p, again);
        assert_eq!(p.methods[0].method_type, MethodType::Microwave);
    }

    #[test]
    fn normalization_trims_and_dedupes() {
        let mut p = sample();
        p.name = "  Pot Pie  ".into();
        p.brand = Some("   ".into());
        p.barcodes = vec!["123456".into(), " 123456 ".into(), "".into()];
        p.tags = vec!["Dinner".into(), "dinner".into()];
        let n = p.normalized().unwrap();
        assert_eq!(n.name, "Pot Pie");
        assert_eq!(n.brand, None);
        assert_eq!(n.barcodes, vec!["123456"]);
        assert_eq!(n.tags, vec!["Dinner"]);
    }

    #[test]
    fn rejects_invalid() {
        let mut p = sample();
        p.rating = Some(9);
        assert!(p.normalized().is_err());
        let mut p = sample();
        p.id = "nope".into();
        assert!(p.normalized().is_err());
        let mut p = sample();
        p.barcodes = vec!["bad code!".into()];
        assert!(p.normalized().is_err());
    }

    #[test]
    fn durations() {
        assert_eq!(format_duration(45), "45 sec");
        assert_eq!(format_duration(420), "7 min");
        assert_eq!(format_duration(90), "1:30");
        assert_eq!(format_duration(3600), "1 hr");
        assert_eq!(format_duration(4500), "1 hr 15 min");
        assert_eq!(sample().summary(), "Microwave · 7 min");
    }
}
