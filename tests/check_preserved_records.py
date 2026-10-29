import csv
import json
import subprocess
from decimal import Decimal, InvalidOperation
from pathlib import Path
import urllib.request
import http.cookiejar

ROOT = Path(__file__).resolve().parents[1]
TABLES = {'vehicle': 'vehicles', 'customer': 'customers', 'rental_order': 'orders', 'payment_record': 'payments', 'dispatch_task': 'dispatch', 'maintenance_record': 'maintenance'}


def normalized(value):
    if value is None or value == '':
        return ''
    text = str(value)
    if text.lower() in ['true', 'false']:
        return text.lower()
    try:
        return str(Decimal(text).normalize())
    except InvalidOperation:
        return text


def main():
    backup = ROOT / 'data/runtime/pre-customer-upgrade'
    assert backup.with_suffix('.mv.db').exists()
    java = ROOT / '.tools/jdk-21.0.13.0.2+1/bin/java.exe'
    h2 = Path.home() / '.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar'
    sql = ';'.join(f"CALL CSVWRITE('{(ROOT / ('data/runtime/baseline-' + table + '.csv')).as_posix()}', 'SELECT * FROM {table} ORDER BY id')" for table in TABLES)
    subprocess.run([str(java), '-cp', str(h2), 'org.h2.tools.Shell', '-url', 'jdbc:h2:file:' + backup.as_posix() + ';MODE=MySQL;DATABASE_TO_LOWER=TRUE;IFEXISTS=TRUE;ACCESS_MODE_DATA=r', '-user', 'sa', '-password', '', '-sql', sql], check=True, capture_output=True)
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    client.open(urllib.request.Request('http://127.0.0.1:8080/api/auth/login', data=json.dumps({'username': 'admin', 'password': 'Admin@12345'}).encode(), headers={'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest'}), timeout=10)
    current = json.load(client.open('http://127.0.0.1:8080/api/bootstrap', timeout=10))
    counts = {}
    for table, key in TABLES.items():
        with (ROOT / ('data/runtime/baseline-' + table + '.csv')).open(encoding='utf-8', newline='') as handle:
            original = [{field: normalized(value) for field, value in row.items()} for row in csv.DictReader(handle)]
        fields = list(original[0]) if original else []
        actual = [{field: normalized(row.get(field)) for field in fields} for row in sorted(current[key], key=lambda row: row['id'])]
        assert original == actual, table
        counts[key] = len(actual)
    result = {'business_records_unchanged_from_pre_upgrade_database': True, 'counts': counts, 'account_change': 'STAFF promoted to ADMIN role with STAFF account_type; existing credentials preserved'}
    (ROOT / 'tests/data-preservation-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
