(() => {
  'use strict';
  const PROJECT_URL = 'https://qfjwtawsqfwmsmrrgqfi.supabase.co';
  const PUBLIC_KEY = 'sb_publishable_-EUVjwsk3txKk2Opqrc7Kw_xFNa9Hw1';
  window.VG_SUPABASE_READY = Promise.resolve(
    window.supabase.createClient(PROJECT_URL, PUBLIC_KEY, {
      auth: {
        persistSession: true,
        autoRefreshToken: true,
        detectSessionInUrl: true,
        storageKey: 'sb-qfjwtawsqfwmsmrrgqfi-auth-token'
      }
    })
  );
})();