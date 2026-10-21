//! Small JNI adapter; filter parsing and matching belong to Brave's maintained engine.
use adblock::{engine::Engine, lists::{FilterSet, ParseOptions}, request::Request};
use jni::{objects::{JClass, JString}, sys::{jboolean, jstring}, JNIEnv};
use std::sync::Mutex;

static ENGINE: Mutex<Option<Engine>> = Mutex::new(None);

fn engine(text: &str) -> Engine {
    let mut filters = FilterSet::new(false);
    filters.add_filter_list(text, ParseOptions::default());
    Engine::new_with_filter_set(filters)
}

fn blocks(engine: &Engine, url: &str, source: &str, kind: &str, method: &str) -> bool {
    Request::new(url, source, kind, method)
        .map(|request| engine.check_network_request(&request).matched)
        .unwrap_or(false)
}

fn selectors(engine: &Engine, url: &str, tokens: &str) -> String {
    let resources = engine.url_cosmetic_resources(url);
    let mut selectors = resources.hide_selectors;
    if !resources.generichide {
        let tokens: serde_json::Value = serde_json::from_str(tokens).unwrap_or_default();
        let strings = |key: &str| -> Vec<String> {
            tokens.get(key).and_then(|v| v.as_array()).into_iter().flatten()
                .take(512).filter_map(|v| v.as_str()).map(str::to_owned).collect()
        };
        selectors.extend(engine.hidden_class_id_selectors(
            strings("classes"), strings("ids"), &resources.exceptions));
    }
    serde_json::to_string(&selectors).unwrap_or_else(|_| "[]".to_owned())
}

fn string(env: &mut JNIEnv, value: JString) -> String {
    env.get_string(&value).map(|s| s.into()).unwrap_or_default()
}

#[no_mangle]
pub extern "system" fn Java_com_aj_firelauncher_NativeBlocker_initialize(
    mut env: JNIEnv, _: JClass, text: JString,
) -> jboolean {
    let text = string(&mut env, text);
    if text.is_empty() { return 0; }
    match ENGINE.lock() {
        Ok(mut slot) => { *slot = Some(engine(&text)); 1 }
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_aj_firelauncher_NativeBlocker_matches(
    mut env: JNIEnv, _: JClass, url: JString, source: JString, kind: JString, method: JString,
) -> jboolean {
    let (url, source, kind, method) = (string(&mut env, url), string(&mut env, source),
        string(&mut env, kind), string(&mut env, method));
    ENGINE.lock().ok().and_then(|slot| slot.as_ref().map(|engine|
        blocks(engine, &url, &source, &kind, &method))).unwrap_or(false) as jboolean
}

#[no_mangle]
pub extern "system" fn Java_com_aj_firelauncher_NativeBlocker_selectors(
    mut env: JNIEnv, _: JClass, url: JString, tokens: JString,
) -> jstring {
    let url = string(&mut env, url);
    let tokens = string(&mut env, tokens);
    let result = if tokens.len() > 32768 { "[]".to_owned() } else {
        ENGINE.lock().ok().and_then(|slot| slot.as_ref().map(|engine|
            selectors(engine, &url, &tokens))).unwrap_or_else(|| "[]".to_owned())
    };
    env.new_string(result).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn real_engine_honors_types_domains_and_exceptions() {
        let e = engine("||ads.example^$script,third-party\n@@||ads.example/allowed.js$domain=imdb.com\nimdb.com##.sponsored\n##.generic-ad\nimdb.com#@#.generic-ad");
        assert!(blocks(&e,"https://ads.example/ad.js","https://imdb.com/","script","get"));
        assert!(!blocks(&e,"https://ads.example/ad.js","https://imdb.com/","image","get"));
        assert!(!blocks(&e,"https://ads.example/allowed.js","https://imdb.com/","script","get"));
        let css = selectors(&e,"https://imdb.com/",r#"{"classes":["generic-ad"],"ids":[]}"#);
        assert!(css.contains(".sponsored"));
        assert!(!css.contains(".generic-ad"));
    }
    #[test]
    fn bundled_lists_block_ads_but_allow_content_and_video() {
        let lists = concat!(include_str!("../../../app/src/main/assets/filters/easylist.txt"),"\n",
            include_str!("../../../app/src/main/assets/filters/easyprivacy.txt"),"\n",
            include_str!("../../../app/src/main/assets/filters/adguard-base.txt"));
        let e = engine(lists);
        assert!(blocks(&e,"https://securepubads.g.doubleclick.net/tag/js/gpt.js","https://www.imdb.com/","script","get"));
        assert!(!blocks(&e,"https://www.imdb.com/title/tt0111161/","https://www.imdb.com/","document","get"));
        assert!(!blocks(&e,"https://example.org/movie.mp4","https://example.org/","media","get"));
        assert!(!selectors(&e,"https://www.imdb.com/", "{}").is_empty());
    }
}
