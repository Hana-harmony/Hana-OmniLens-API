DELETE FROM portal_users
WHERE username = 'admin'
  AND role = 'ADMIN'
  AND password_hash = '{bcrypt}$2y$12$QYdm5Z2QBMF/9XgtNMvA5umnErMvlTRskDzg4U5wcIN5PH.X9Sf/K'
  AND password_change_required = TRUE
  AND password_changed_at IS NULL;
