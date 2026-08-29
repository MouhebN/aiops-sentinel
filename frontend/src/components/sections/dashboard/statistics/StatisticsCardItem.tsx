import { Box, Card, Grid, Stack, Typography } from '@mui/material';
import IconifyIcon from 'components/base/IconifyIcon';
import { currencyFormat, percentageFormat } from 'helpers/utils';
import { useBreakpoints } from 'providers/useBreakPoint';
import { IStatisticsCard } from './StatisticCards';

interface StatisticsCardProps {
  cardData: IStatisticsCard;
  index: number;
  totalCount?: number;
}

const StatisticsCardItem = ({ cardData, index }: StatisticsCardProps) => {
  const { title, subtitle, percentage, icon: IconComponent } = cardData || {};
  const { up } = useBreakpoints();
  const upXl = up('xl');
  const upSm = up('sm');
  const upXs = up('xs');
  return (
    <>
      <Card
        sx={{
          borderRadius: 4,
          ...(upXs && {
            borderTopLeftRadius: index === 0 ? 8 : 0,
            borderTopRightRadius: index === 0 ? 8 : 0,
            borderBottomLeftRadius: index === 3 ? 8 : 0,
            borderBottomRightRadius: index === 3 ? 8 : 0,
          }),
          ...(upSm && {
            borderTopLeftRadius: index === 0 ? 8 : 0,
            borderBottomLeftRadius: index === 2 ? 8 : 0,
            borderTopRightRadius: index === 1 ? 8 : 0,
            borderBottomRightRadius: index === 3 ? 8 : 0,
          }),
          ...(upXl && {
            borderTopLeftRadius: index === 0 ? 8 : 0,
            borderBottomLeftRadius: index === 0 ? 8 : 0,
            borderTopRightRadius: index === 3 ? 8 : 0,
            borderBottomRightRadius: index === 3 ? 8 : 0,
          }),
        }}
      >
        <Box
          sx={{
            p: 4,
            flexGrow: 1,
          }}
        >
          <Grid container alignItems="center" spacing={1}>
            <Grid item xs={8}>
              <Box ml={0} lineHeight={1}>
                <Typography variant="h5" textTransform="uppercase" color="text.primary">
                  {currencyFormat(parseFloat(title))}
                </Typography>
                <Typography variant="subtitle1" color="text.secondary" mb={1}>
                  {subtitle}
                </Typography>
              </Box>
              <Stack direction="row" alignItems="center">
                <Typography variant="subtitle1" color={`${percentage.color}.dark`}>
                  {percentageFormat(parseFloat(percentage.count))}
                </Typography>
                {percentage.color !== 'success' ? (
                  <IconifyIcon
                    color={`${percentage.color}.dark`}
                    icon="iconamoon:arrow-down-2-light"
                    width={24}
                    height={24}
                  />
                ) : (
                  <IconifyIcon
                    color={`${percentage.color}.main`}
                    icon="iconamoon:arrow-up-2-light"
                    width={24}
                    height={24}
                  />
                )}
              </Stack>
            </Grid>
            <Grid item xs={4}>
              <Stack direction="row" justifyContent="flex-end" alignItems="center" ml="auto">
                <Box
                  sx={{
                    display: 'grid',
                    width: 56,
                    height: 56,
                    borderRadius: '50%',
                    placeItems: 'center',
                    bgcolor: 'primary.lighter',
                  }}
                >
                  <IconComponent color="primary" />
                </Box>
              </Stack>
            </Grid>
          </Grid>
        </Box>
      </Card>
    </>
  );
};

export default StatisticsCardItem;
