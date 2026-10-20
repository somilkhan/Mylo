/**
 * Party invites: `https://bitchord.kushagrasingh.in/invite/<CODE>`,
 * optionally `?server=<custom server>`, and `mylo://party/<CODE>` (legacy
 * `bitchord://party/<CODE>` remains accepted). On the web the HTTPS URL is a
 * route of this app (`/invite/:code`).
 */

export const INVITE_ORIGIN = 'https://bitchord.kushagrasingh.in';
export const CODE_LENGTH = 6;

/** Letters and digits only, upper case; null unless it's six of them. */
export function cleanCode(raw: string): string | null {
  const cleaned = raw.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  return cleaned.length === CODE_LENGTH ? cleaned : null;
}

export function sanitizeServerUrl(raw: string | null | undefined): string | null {
  const trimmed = raw?.trim().replace(/\\/+$/, '');
  if (!trimmed) return null;
  const withScheme = /^https?:\\/\\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  // URL normalizes literal dot segments before exposing pathname, so inspect
  // the supplied path first to keep validation consistent with Android.
  const rawPath = withScheme.match(/^https?:\\/\\/[^/?#]*(\\/[^?#]*)?/i)?.[1] ?? '';
  if (rawPath.split('/').some((segment) => segment === '.' || segment === '..')) return null;
  try {
    const url = new URL(withScheme);
    // Server URLs may include a reverse-proxy path, but never credentials or
    // query/fragment data. Those parts are not part of the server origin and
    // can make shared invite links ambiguous or leak secrets.
    if (!['http:', 'https:'].includes(url.protocol) || !url.hostname) return null;
    if (url.username || url.password || url.search || url.hash) return null;
    const hostname = url.hostname.toLowerCase();
    // Match Android's server validator: localhost is useful for development,
    // IPv6 literals are valid, and other hosts must be dotted DNS/IP names.
    if (
      !hostname.startsWith('[') &&
      hostname !== 'localhost' &&
      !hostname.includes('.')
    ) return null;
    const path = url.pathname.replace(/\\/+$/, '');
    if (path.split('/').some((segment) => segment === '.' || segment === '..')) return null;
    return `${url.origin}${path}`;
  } catch {
    return null;
  }
}

export function parseInvite(value: string): { code: string; serverUrl: string | null } | null {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return null;
  }
  const server = sanitizeServerUrl(url.searchParams.get('server'));
  if ((url.protocol === 'mylo:' || url.protocol === 'bitchord:') && url.host.toLowerCase() === 'party') {
    const code = cleanCode(url.pathname.replace(/^\/+|\/+$/g, '') || url.searchParams.get('code') || '');
    return code ? { code, serverUrl: server } : null;
  }
  const match = url.pathname.match(/^\/invite\/([A-Za-z0-9]{6})$/);
  if ((url.protocol === 'https:' || url.protocol === 'http:') && match) return { code: match[1]!.toUpperCase(), serverUrl: server };
  return null;
}

/** The link to share (JamInviteLink.url): a custom server's own invite page, else the public site's. */
export function inviteUrl(code: string, customServer: string | null): string {
  const base = customServer?.trim().replace(/\/+$/, '');
  const origin = base && base.toLowerCase() !== INVITE_ORIGIN ? base : INVITE_ORIGIN;
  return `${origin}/invite/${code.toUpperCase()}`;
}
