/**
 * Cloudflare Worker: FCM Push Notification Sender for Tether (@rvmeur)
 *
 * Murni JavaScript (ES Module, WebCrypto RS256, Fetch API) tanpa dependensi npm.
 * Rahasia disimpan dalam Cloudflare Worker Secrets (GOOGLE_SERVICE_ACCOUNT_JSON & SENDER_SECRET).
 */

const PROJECT_ID = 'happyfamily-ryndxtr';
const FCM_SEND_ENDPOINT = `https://fcm.googleapis.com/v1/projects/${PROJECT_ID}/messages:send`;
const GOOGLE_TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const GOOGLE_FCM_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

/**
 * Sensor token / string rahasia untuk log console (hanya 6 char awal & 6 char akhir)
 */
function maskSecret(str) {
  if (!str || typeof str !== 'string') return '(empty)';
  if (str.length <= 12) return '******';
  return `${str.slice(0, 6)}...${str.slice(-6)}`;
}

/**
 * Perbandingan string secara timing-safe sederhana untuk menghindari timing attacks pada token auth
 */
function timingSafeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const enc = new TextEncoder();
  const bufA = enc.encode(a);
  const bufB = enc.encode(b);
  if (bufA.byteLength !== bufB.byteLength) return false;

  let mismatch = 0;
  for (let i = 0; i < bufA.byteLength; i++) {
    mismatch |= bufA[i] ^ bufB[i];
  }
  return mismatch === 0;
}

/**
 * Encode string atau Uint8Array/ArrayBuffer ke Base64URL (RFC 4648 § 5)
 */
function base64UrlEncode(input) {
  let bytes;
  if (typeof input === 'string') {
    bytes = new TextEncoder().encode(input);
  } else if (input instanceof ArrayBuffer) {
    bytes = new Uint8Array(input);
  } else if (ArrayBuffer.isView(input)) {
    bytes = new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
  } else {
    throw new TypeError('Invalid input type for base64UrlEncode');
  }

  let binary = '';
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }

  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/**
 * Mengubah string private_key PEM (PKCS#8) menjadi ArrayBuffer binary DER
 */
function pemToArrayBuffer(pem) {
  const cleanBase64 = pem
    .replace(/-----BEGIN[ A-Z0-9_-]+-----/g, '')
    .replace(/-----END[ A-Z0-9_-]+-----/g, '')
    .replace(/\s+/g, '');
  const binaryString = atob(cleanBase64);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes.buffer;
}

/**
 * Membuat JWT RS256 bertandatangan WebCrypto dari Service Account Google
 */
async function createServiceAccountJwt(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const header = {
    alg: 'RS256',
    typ: 'JWT',
  };

  const claims = {
    iss: serviceAccount.client_email,
    scope: GOOGLE_FCM_SCOPE,
    aud: GOOGLE_TOKEN_ENDPOINT,
    exp: now + 3600, // Valid selama 1 jam
    iat: now,
  };

  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedClaims = base64UrlEncode(JSON.stringify(claims));
  const dataToSign = `${encodedHeader}.${encodedClaims}`;

  const keyBuffer = pemToArrayBuffer(serviceAccount.private_key);
  const cryptoKey = await crypto.subtle.importKey(
    'pkcs8',
    keyBuffer,
    {
      name: 'RSASSA-PKCS1-v1_5',
      hash: 'SHA-256',
    },
    false,
    ['sign']
  );

  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    cryptoKey,
    new TextEncoder().encode(dataToSign)
  );

  return `${dataToSign}.${base64UrlEncode(signature)}`;
}

/**
 * Menukar JWT assertion ke Google OAuth2 Access Token
 */
async function getGoogleAccessToken(serviceAccount) {
  const assertionJwt = await createServiceAccountJwt(serviceAccount);

  const params = new URLSearchParams();
  params.set('grant_type', 'urn:ietf:params:oauth:grant-type:jwt-bearer');
  params.set('assertion', assertionJwt);

  const res = await fetch(GOOGLE_TOKEN_ENDPOINT, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: params.toString(),
  });

  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`Google OAuth2 error (${res.status}): ${errText}`);
  }

  const tokenData = await res.json();
  if (!tokenData.access_token) {
    throw new Error('Google OAuth2 response tidak mengandung access_token');
  }

  return tokenData.access_token;
}

/**
 * Helper pembuat JSON Response
 */
function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'no-store',
    },
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Endpoint status GET /
    if (request.method === 'GET' && url.pathname === '/') {
      return new Response('tether fcm sender aktif', {
        status: 200,
        headers: {
          'Content-Type': 'text/plain; charset=utf-8',
        },
      });
    }

    // Hanya tangani POST /send
    if (url.pathname !== '/send') {
      return jsonResponse({ ok: false, error: 'Endpoint tidak ditemukan' }, 404);
    }

    if (request.method !== 'POST') {
      return jsonResponse({ ok: false, error: 'Metode HTTP tidak didukung' }, 405);
    }

    // 1. Verifikasi Autentikasi Bearer SENDER_SECRET
    const expectedSecret = env.SENDER_SECRET;
    if (!expectedSecret) {
      console.error('[AUTH] SENDER_SECRET belum dikonfigurasi di environment worker');
      return jsonResponse({ ok: false, error: 'Konfigurasi server belum lengkap' }, 500);
    }

    const authHeader = request.headers.get('Authorization') || '';
    const match = authHeader.match(/^Bearer\s+(.+)$/i);
    const providedSecret = match ? match[1].trim() : '';

    if (!providedSecret || !timingSafeEqual(providedSecret, expectedSecret)) {
      return jsonResponse({ ok: false, error: 'Unauthorized' }, 401);
    }

    // 2. Parsing dan Validasi Request Body
    let bodyJson;
    try {
      bodyJson = await request.json();
    } catch {
      return jsonResponse({ ok: false, error: 'Body request bukan JSON yang valid' }, 400);
    }

    const { token, title, body } = bodyJson || {};

    if (
      typeof token !== 'string' ||
      typeof title !== 'string' ||
      typeof body !== 'string' ||
      token.trim().length === 0 ||
      title.trim().length === 0 ||
      body.trim().length === 0
    ) {
      return jsonResponse(
        { ok: false, error: 'Parameter token, title, dan body wajib diisi (string non-kosong)' },
        400
      );
    }

    if (token.length > 4096) {
      return jsonResponse({ ok: false, error: 'Panjang token melebihi batas 4096 karakter' }, 400);
    }

    // 3. Parsing Service Account dari Environment
    const serviceAccountRaw = env.GOOGLE_SERVICE_ACCOUNT_JSON;
    if (!serviceAccountRaw) {
      console.error('[FCM] GOOGLE_SERVICE_ACCOUNT_JSON belum dikonfigurasi');
      return jsonResponse({ ok: false, error: 'Service account belum dikonfigurasi di worker' }, 500);
    }

    let serviceAccount;
    try {
      serviceAccount = JSON.parse(serviceAccountRaw);
    } catch {
      console.error('[FCM] Format GOOGLE_SERVICE_ACCOUNT_JSON tidak valid JSON');
      return jsonResponse({ ok: false, error: 'Format service account di worker tidak valid' }, 500);
    }

    if (!serviceAccount.client_email || !serviceAccount.private_key) {
      console.error('[FCM] Service account kehilangan client_email atau private_key');
      return jsonResponse({ ok: false, error: 'Service account tidak memiliki atribut penting' }, 500);
    }

    // 4. Peroleh Access Token dan Kirim ke FCM v1
    const maskedTargetToken = maskSecret(token);
    console.log(`[FCM] Memproses pengiriman notifikasi ke target: ${maskedTargetToken}`);

    try {
      const accessToken = await getGoogleAccessToken(serviceAccount);

      const fcmPayload = {
        message: {
          token: token.trim(),
          notification: {
            title: title.trim(),
            body: body.trim(),
          },
        },
      };

      const fcmResponse = await fetch(FCM_SEND_ENDPOINT, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json; charset=utf-8',
        },
        body: JSON.stringify(fcmPayload),
      });

      if (!fcmResponse.ok) {
        const errorBody = await fcmResponse.text();
        console.error(`[FCM] Google FCM API error (${fcmResponse.status}) untuk token ${maskedTargetToken}: ${errorBody}`);

        let parsedErrorMsg = errorBody;
        try {
          const parsed = JSON.parse(errorBody);
          if (parsed.error && parsed.error.message) {
            parsedErrorMsg = parsed.error.message;
          }
        } catch {
          // Tetap gunakan string pesan mentah jika gagal parse
        }

        return jsonResponse(
          { ok: false, error: `FCM kirim gagal: ${parsedErrorMsg}` },
          fcmResponse.status >= 400 && fcmResponse.status < 500 ? fcmResponse.status : 502
        );
      }

      const fcmResult = await fcmResponse.json();
      console.log(`[FCM] Berhasil dikirim ke target: ${maskedTargetToken}, messageId: ${fcmResult.name || 'ok'}`);

      return jsonResponse({
        ok: true,
        name: fcmResult.name,
      });
    } catch (err) {
      console.error(`[FCM] Exception saat pengiriman: ${err.message}`);
      return jsonResponse({ ok: false, error: `Internal error: ${err.message}` }, 500);
    }
  },
};
