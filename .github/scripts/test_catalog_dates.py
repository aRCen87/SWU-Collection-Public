import unittest
from catalog_dates import load_release_dates,release_date_for,valid_date
class CatalogDatesTest(unittest.TestCase):
 def test_unknown_dates_are_not_invented_or_erased(self):
  self.assertEqual('',release_date_for('P26',{}))
  self.assertEqual('2027-02-05',release_date_for('NEW',{},previous='2027-02-05'))
  self.assertEqual('2027-01-08',release_date_for('NEW',{},upstream='2027-01-08',previous='2026-12-04'))
  for value in ['2026-02-30','2026-9-1','2026-01-01T00:00:00Z',None]:self.assertEqual('',valid_date(value))
 def test_official_dates_override_unverified_metadata(self):
  dates=load_release_dates()
  self.assertEqual('2026-10-09',release_date_for('HMW',dates,upstream='2026-09-16'))
  self.assertEqual('2025-10-03',dates['IBH'])
  self.assertNotIn('P26',dates)
