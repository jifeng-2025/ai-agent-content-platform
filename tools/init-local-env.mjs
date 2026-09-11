import {existsSync,readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
if(!existsSync('.env')){
 writeFileSync('.env',new TextDecoder('utf-8',{fatal:true}).decode(readFileSync('.env.example')).replace('MYSQL_ROOT_PASSWORD=','MYSQL_ROOT_PASSWORD='+randomBytes(24).toString('base64url')),{flag:'wx',mode:0o600});
 console.log('Created .env with random local database password. Log in as administrator to configure text/image models in the web UI; no model key is needed to start. Demo images are placeholders. No keys printed.');
}else console.log('Existing .env preserved.');
const front='frontend/src/config/env.ts';
if(!existsSync(front)){
 mkdirSync('frontend/src/config',{recursive:true});
 writeFileSync(front,"export const API_BASE_URL = '/api'\n",{flag:'wx'});
 console.log('Created same-origin frontend API configuration.');
}else console.log('Existing frontend API configuration preserved.');
