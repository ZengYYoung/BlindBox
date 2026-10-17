-- Read-only audit: run only after connecting the intended database.
SELECT id,balance FROM user WHERE balance<0;
SELECT id,stock FROM prize WHERE stock<0;
SELECT b.id,b.stock,COALESCE(SUM(p.stock),0) AS prize_total
FROM blind_box b LEFT JOIN prize p ON p.blind_box_id=b.id
GROUP BY b.id,b.stock HAVING b.stock<>prize_total;
SELECT user_id,request_id,COUNT(*) AS n FROM orders GROUP BY user_id,request_id HAVING n>1;
-- legacy-* historical orders predate ledgers and are intentionally excluded.
SELECT o.id FROM orders o LEFT JOIN wallet_ledger w ON w.order_id=o.id AND w.type='DRAW'
WHERE o.request_id NOT LIKE 'legacy-%' GROUP BY o.id HAVING COUNT(w.id)<>1;
SELECT o.id FROM orders o LEFT JOIN stock_ledger s ON s.order_id=o.id AND s.reason='DRAW'
WHERE o.request_id NOT LIKE 'legacy-%' GROUP BY o.id HAVING COUNT(s.id)<>1;
SELECT o.id,o.price,w.amount FROM orders o JOIN wallet_ledger w ON w.order_id=o.id
WHERE w.type='DRAW' AND w.amount<>-o.price;
SELECT o.id FROM orders o JOIN stock_ledger s ON s.order_id=o.id
WHERE s.reason='DRAW' AND (s.delta<>-1 OR s.prize_id<>o.prize_id OR s.blind_box_id<>o.blind_box_id);
-- After migration old balances/likes are opening values, so do not assume full historical reconstruction.
