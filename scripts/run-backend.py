#!/usr/bin/env python3
"""Load a local .env without shell evaluation; never prints credentials."""
import argparse
import os
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[1]
backend = root / 'blindbox-springboot'
parser = argparse.ArgumentParser()
parser.add_argument('--env', type=Path, default=backend / '.env')
parser.add_argument('--jar', action='store_true')
args = parser.parse_args()
allowed = {'DB_URL','DB_USER','DB_PASSWORD','DB_POOL_SIZE','REDIS_HOST','REDIS_PORT','REDIS_PASSWORD',
           'JWT_SECRET','JWT_HOURS','DEMO_RECHARGE_ENABLED','RATE_LIMIT_ENABLED','UPLOAD_DIR','CORS_ORIGINS','SERVER_PORT','HTTP_MAX_INFLIGHT','DRAW_MAX_INFLIGHT','DRAW_MAX_PER_POOL',
           'ENTRY_READ_PER_MINUTE','ENTRY_WRITE_PER_MINUTE','ENTRY_AUTH_PER_MINUTE'}
environment = os.environ.copy()
if not args.env.exists():
    parser.error('Create blindbox-springboot/.env from .env.example first.')
for line in args.env.read_text().splitlines():
    line = line.strip()
    if not line or line.startswith('#'):
        continue
    key, separator, value = line.partition('=')
    key, value = key.strip(), value.strip()
    if not separator or key not in allowed:
        parser.error('Unsupported environment key: ' + key)
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ['"', "'"]:
        value = value[1:-1]
    environment[key] = value
command = ['java', '-jar', 'target/blindbox-springboot-1.0.0.jar'] if args.jar else ['mvn','-s','.mvn/settings.xml','spring-boot:run']
raise SystemExit(subprocess.call(command, cwd=backend, env=environment))
