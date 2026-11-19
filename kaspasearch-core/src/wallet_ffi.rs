use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int};

pub const WALLET_BRIDGE_VERSION: u32 = 1;

/// Validates whether a given address string is a syntactically valid Kaspa address.
/// Returns 1 if valid, 0 if invalid.
///
/// # Safety
/// `address_ptr` must be null or point to a readable, NUL-terminated C string.
#[no_mangle]
pub extern "C" fn kaspa_wallet_validate_address(address_ptr: *const c_char) -> c_int {
    if address_ptr.is_null() {
        return 0;
    }
    // SAFETY: The caller contract above guarantees a readable NUL-terminated string.
    // nosemgrep: rust.lang.security.unsafe-usage.unsafe-usage
    let c_str = unsafe { CStr::from_ptr(address_ptr) };
    let addr = match c_str.to_str() {
        Ok(s) => s.trim(),
        Err(_) => return 0,
    };

    if addr.len() < 61 || addr.len() > 70 {
        return 0;
    }

    if addr.starts_with("kaspa:")
        || addr.starts_with("kaspatest:")
        || addr.starts_with("kaspadev:")
        || addr.starts_with("kaspasim:")
    {
        1
    } else {
        0
    }
}

/// Builds a standard KCC-20 covenant transfer payload.
/// Writes result into out_buf (up to out_len bytes).
/// Returns number of bytes written, or -1 on error.
///
/// # Safety
/// Each input pointer must be non-null and point to a readable, NUL-terminated C string.
/// `out_buf` must point to a writable allocation of at least `out_len` bytes.
/// The caller must ensure all pointed-to allocations remain valid for this call.
#[no_mangle]
pub extern "C" fn kaspa_wallet_build_kcc20_payload(
    symbol_ptr: *const c_char,
    amount_ptr: *const c_char,
    recipient_ptr: *const c_char,
    out_buf: *mut c_char,
    out_len: usize,
) -> c_int {
    if symbol_ptr.is_null()
        || amount_ptr.is_null()
        || recipient_ptr.is_null()
        || out_buf.is_null()
        || out_len == 0
    {
        return -1;
    }

    // SAFETY: The function's caller contract guarantees valid NUL-terminated input strings.
    // nosemgrep: rust.lang.security.unsafe-usage.unsafe-usage
    let symbol = match unsafe { CStr::from_ptr(symbol_ptr) }.to_str() {
        Ok(s) => s.trim().to_uppercase(),
        Err(_) => return -1,
    };
    // SAFETY: The function's caller contract guarantees valid NUL-terminated input strings.
    // nosemgrep: rust.lang.security.unsafe-usage.unsafe-usage
    let amount = match unsafe { CStr::from_ptr(amount_ptr) }.to_str() {
        Ok(s) => s.trim(),
        Err(_) => return -1,
    };
    // SAFETY: The function's caller contract guarantees valid NUL-terminated input strings.
    // nosemgrep: rust.lang.security.unsafe-usage.unsafe-usage
    let recipient = match unsafe { CStr::from_ptr(recipient_ptr) }.to_str() {
        Ok(s) => s.trim(),
        Err(_) => return -1,
    };

    let payload = serde_json::json!({
        "p": "kcc-20",
        "op": "transfer",
        "tick": symbol,
        "amt": amount,
        "to": recipient
    })
    .to_string();

    let bytes = payload.as_bytes();
    if bytes.len() >= out_len || bytes.len() > c_int::MAX as usize {
        return -1;
    }

    // SAFETY: The caller guarantees out_buf is writable for out_len bytes. The
    // preceding check guarantees room for the payload and its trailing NUL byte.
    // nosemgrep: rust.lang.security.unsafe-usage.unsafe-usage
    unsafe {
        std::ptr::copy_nonoverlapping(bytes.as_ptr().cast::<c_char>(), out_buf, bytes.len());
        *out_buf.add(bytes.len()) = 0;
    }

    bytes.len() as c_int
}

/// Formats sompi (1 KAS = 100,000,000 sompi) into standard decimal string.
///
/// # Safety
/// `out_buf` must point to a writable allocation of at least `out_len` bytes.
/// The caller must ensure the allocation remains valid for this call.
#[no_mangle]
pub extern "C" fn kaspa_wallet_format_sompi(
    amount_sompi: u64,
    out_buf: *mut c_char,
    out_len: usize,
) -> c_int {
    if out_buf.is_null() || out_len == 0 {
        return -1;
    }

    let kas = amount_sompi / 100_000_000;
    let rem = amount_sompi % 100_000_000;
    let formatted = format!("{}.{:08}", kas, rem);

    let bytes = formatted.as_bytes();
    if bytes.len() >= out_len || bytes.len() > c_int::MAX as usize {
        return -1;
    }

    // SAFETY: The caller guarantees out_buf is writable for out_len bytes. The
    // preceding check guarantees room for the formatted bytes and trailing NUL.
    // nosemgrep: rust.lang.security.unsafe-usage.unsafe-usage
    unsafe {
        std::ptr::copy_nonoverlapping(bytes.as_ptr().cast::<c_char>(), out_buf, bytes.len());
        *out_buf.add(bytes.len()) = 0;
    }

    bytes.len() as c_int
}

/// Returns the wallet bridge protocol version.
#[no_mangle]
pub extern "C" fn kaspa_wallet_version() -> u32 {
    WALLET_BRIDGE_VERSION
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_validate_address() {
        let valid = CString::new(
            "kaspa:qpzry9x8gf2tvdw0s3jn54khce6mua7lcw20uq3wh5q29bdqq97jye27u28",
        )
        .unwrap();
        assert_eq!(kaspa_wallet_validate_address(valid.as_ptr()), 1);

        let invalid = CString::new("invalid_address").unwrap();
        assert_eq!(kaspa_wallet_validate_address(invalid.as_ptr()), 0);
    }

    #[test]
    fn test_format_sompi() {
        let mut buf = [0i8; 64];
        let len = kaspa_wallet_format_sompi(100_000_000, buf.as_mut_ptr(), buf.len());
        assert!(len > 0);
        let output = buf[..len as usize]
            .iter()
            .map(|byte| *byte as u8)
            .collect::<Vec<_>>();
        let s = String::from_utf8(output).unwrap();
        assert_eq!(s, "1.00000000");
    }
}
