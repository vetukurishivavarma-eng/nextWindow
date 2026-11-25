#!/usr/bin/env node
// Generates a VAPID key pair for Web Push using only Node's built-in crypto.
import { generateKeyPairSync } from 'node:crypto';

const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const pub = publicKey.export({ format: 'jwk' });
const priv = privateKey.export({ format: 'jwk' });
const b64u = (b) => Buffer.from(b).toString('base64url');
const raw = Buffer.concat([Buffer.from([4]), Buffer.from(pub.x, 'base64url'), Buffer.from(pub.y, 'base64url')]);

console.log(`VAPID_PUBLIC_KEY=${b64u(raw)}`);
console.log(`VAPID_PRIVATE_KEY=${priv.d}`);
