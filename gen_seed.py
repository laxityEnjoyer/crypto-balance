# gen_seed.py
import random, uuid, datetime, os

ADDRESSES = [
    "TDqSquXBgUCLYvYC4XZgrprLK589dkhSCf",  # Binance (sample)
    "TJ5usJLLwjwn7Pw3TPbdzreG7dvgKzfQ5y",  # Binance (sample)
    "TRjE1H8dxypKM1NZRdysbs9wo7huR4bdNz",  # example
    "TPAe77oEGDLXuNjJhTyYeo5vMqLYdE3GN8U", # example
    "TNuTtmTdmdGWKQSnySroeXjyzGh1ZsRau2",  # example
    "TPAgKfYzRdK83Qocc4gXvEVu4jPKfeuer5"   # example
]
TOKENS = ["USDT", "TRX"]

random.seed(12345)

lines = []
lines.append("USE trx;")
lines.append("TRUNCATE transaction_address_amount;")

# 1) Realny duży transfer USDT (para wpisów +/-)
REAL_BLOCK = 47000000
REAL_TX = "946a4feeabee8c7de8ae365769d11c316c1a027668c232da1a158405823cf7be"
REAL_AMOUNT = 61017840 * 1_000_000  # 61,017,840 USDT w 1e-6
sender = ADDRESSES[0]; receiver = ADDRESSES[1]
lines.append(f"INSERT INTO trx.transaction_address_amount (chain, address, token_name, block_number, tx_hash, amount_delta) VALUES ('TRON','{sender}','USDT',{REAL_BLOCK},'{REAL_TX}', -{REAL_AMOUNT});")
lines.append(f"INSERT INTO trx.transaction_address_amount (chain, address, token_name, block_number, tx_hash, amount_delta) VALUES ('TRON','{receiver}','USDT',{REAL_BLOCK},'{REAL_TX}', {REAL_AMOUNT});")

# 2) ~196 realistycznych transakcji (98 par: nadawca/odbiorca)
START_BLOCK = 46999000
for i in range(98):
    token = random.choice(TOKENS)
    s, r = random.sample(ADDRESSES, 2)
    block = START_BLOCK + random.randint(0, 1200)
    txh = uuid.uuid4().hex
    if token == "TRX":
        units = random.choice([50, 100, 200, 350, 500, 1000, 1500, 2000])  # do ~2000 TRX
    else:
        units = random.choice([50, 100, 200, 350, 500, 1000, 2500, 10000, 250000, 500000, 2000000])
    amount = units * 1_000_000
    lines.append(f"INSERT INTO trx.transaction_address_amount (chain, address, token_name, block_number, tx_hash, amount_delta) VALUES ('TRON','{s}','{token}',{block},'{txh}', -{amount});")
    lines.append(f"INSERT INTO trx.transaction_address_amount (chain, address, token_name, block_number, tx_hash, amount_delta) VALUES ('TRON','{r}','{token}',{block},'{txh}', {amount});")

out = "seed_transactions_real.cql"
with open(out, "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")

print(f"OK -> {out} ({len(lines)-2} INSERTów)")
